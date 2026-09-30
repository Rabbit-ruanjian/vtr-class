package com.vtr.service;

import com.vtr.entity.Courseware;
import com.vtr.event.CoursewareChangedEvent;
import com.vtr.repository.CoursewareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

/**
 * 课件上传/修改/审核后的 AI 索引入口。
 *
 * <p>文件抽取和 Embedding 放在事务提交后的异步线程中执行，避免上传接口被慢速向量服务阻塞。
 * 课程资源只有在课件审核通过且索引成功后，才会进入学生可检索的 READY 集合。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursewareAiIndexListener {

    private static final String COURSEWARE_PREFIX = "/uploads/";
    private static final Set<String> TEXT_DOCUMENTS = Set.of(
            "pdf", "doc", "ppt", "pptx", "docx", "txt", "md"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "ogg", "mov", "m4v"
    );
    private static final Set<String> SUBTITLE_EXTENSIONS = Set.of("srt", "vtt", "txt");

    private final CoursewareRepository coursewareRepository;
    private final DocumentTextExtractor documentTextExtractor;
    private final AiDocumentService aiDocumentService;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    @Async("coursewareAiIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoursewareChanged(CoursewareChangedEvent event) {
        if (event == null || event.coursewareId() == null) return;
        try {
            Courseware courseware = coursewareRepository.findById(event.coursewareId()).orElse(null);
            if (courseware == null) return;
            if (event.reindexContent()) {
                index(courseware);
            } else {
                aiDocumentService.syncCoursewarePublication(courseware);
            }
        } catch (Exception exception) {
            log.error("课件 AI 索引失败：coursewareId={}", event.coursewareId(), exception);
            coursewareRepository.findById(event.coursewareId()).ifPresent(courseware ->
                    aiDocumentService.markCoursewareIndexFailed(courseware, safeMessage(exception)));
        }
    }

    private void index(Courseware courseware) throws IOException {
        if ("DELETED".equalsIgnoreCase(courseware.getStatus())) {
            aiDocumentService.markCoursewareDeleted(courseware.getId());
            return;
        }

        String extension = extensionOf(courseware.getFileName(), courseware.getFileType());
        Path storedFile = resolveStoredFile(courseware.getFileUrl());
        String metadata = coursewareMetadata(courseware);

        if (VIDEO_EXTENSIONS.contains(extension)) {
            String transcript = extractVideoTranscript(storedFile, extension);
            String text = StringUtils.hasText(transcript)
                    ? metadata + "\n\n[视频字幕/转写]\n" + transcript
                    : metadata;
            aiDocumentService.indexCourseware(courseware, text,
                    StringUtils.hasText(transcript) ? "TEXT_READY" : "METADATA_ONLY");
            if (!StringUtils.hasText(transcript)) {
                aiDocumentService.markCoursewareIndexStatus(courseware, "WAITING_TRANSCRIPT",
                        "视频已建立标题、章节和说明索引；要检索视频讲解内容，请上传同名 SRT/VTT/TXT 字幕或接入语音转写服务");
            }
            return;
        }

        if (TEXT_DOCUMENTS.contains(extension)) {
            if (storedFile == null || !Files.isRegularFile(storedFile)) {
                throw new IOException("课件文件不存在，无法建立 AI 索引");
            }
            String extracted = documentTextExtractor.extract(storedFile, courseware.getFileName());
            aiDocumentService.indexCourseware(courseware, extracted, "TEXT_READY");
            return;
        }

        // 图片、压缩包等资源没有可直接送入当前文本 RAG 的正文，仍索引标题/章节/说明，
        // 让“这个资源是什么”类问题可检索，同时明确不把元数据冒充正文。
        if (StringUtils.hasText(metadata)) {
            aiDocumentService.indexCourseware(courseware, metadata, "METADATA_ONLY");
            aiDocumentService.markCoursewareIndexStatus(courseware, "WAITING_OCR",
                    "该资源已建立元数据索引；图片或压缩包正文需要后续接入 OCR/解压解析");
        }
    }

    private String extractVideoTranscript(Path videoFile, String extension) throws IOException {
        if (videoFile == null || !Files.isRegularFile(videoFile)) return "";
        String baseName = videoFile.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        Path directory = videoFile.getParent();
        if (directory == null) return "";
        for (String subtitleExtension : SUBTITLE_EXTENSIONS) {
            Path sidecar = directory.resolve(stem + "." + subtitleExtension).normalize();
            if (Files.isRegularFile(sidecar)) {
                return documentTextExtractor.extractSubtitle(sidecar);
            }
        }
        log.info("视频没有找到同名字幕/转写文件，暂按视频元数据建立索引：coursewareFile={}, extension={}",
                videoFile.getFileName(), extension);
        return "";
    }

    private Path resolveStoredFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith(COURSEWARE_PREFIX)
                || fileUrl.contains("..") || fileUrl.contains("\\")) return null;
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        String relative = fileUrl.substring(COURSEWARE_PREFIX.length())
                .replace('/', java.io.File.separatorChar);
        Path target = base.resolve(relative).normalize();
        return target.startsWith(base) ? target : null;
    }

    private String coursewareMetadata(Courseware courseware) {
        StringBuilder text = new StringBuilder("[课程资源元数据]\n");
        append(text, "标题", courseware.getTitle());
        append(text, "章节", courseware.getChapter());
        append(text, "知识点", courseware.getKnowledgePoint());
        append(text, "资源类型", courseware.getResourceType());
        append(text, "文件名", courseware.getFileName());
        append(text, "说明", courseware.getDescription());
        return text.toString().trim();
    }

    private void append(StringBuilder target, String label, String value) {
        if (StringUtils.hasText(value)) target.append(label).append("：").append(value.trim()).append('\n');
    }

    private String extensionOf(String fileName, String fileType) {
        String extension = StringUtils.getFilenameExtension(fileName);
        if (!StringUtils.hasText(extension)) extension = fileType;
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? "未知索引错误" : exception.getMessage();
        if (!StringUtils.hasText(message)) return "未知索引错误";
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
