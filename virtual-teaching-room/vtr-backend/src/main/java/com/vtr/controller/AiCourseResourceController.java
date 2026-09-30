package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.entity.AiDocument;
import com.vtr.repository.AiDocumentRepository;
import com.vtr.service.AiDocumentService;
import com.vtr.service.DocumentTextExtractor;
import com.vtr.vo.AiKnowledgeResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.List;

@RestController
@RequestMapping("/api/ai/course-resources")
@RequiredArgsConstructor
public class AiCourseResourceController {
    private static final long MAX_COURSE_RESOURCE_SIZE = 50L * 1024 * 1024;
    private final AiDocumentService aiDocumentService;
    private final AiDocumentRepository aiDocumentRepository;
    private final DocumentTextExtractor documentTextExtractor;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Result<AiKnowledgeResourceVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String chapter,
            @RequestParam(required = false, defaultValue = "OPEN_RESOURCE") String sourceType,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String license,
        @RequestParam(required = false) String sourceAuthor,
            @RequestParam(required = false) String attribution) {
        try {
            validateCourseResourceFile(file);
            String name = StringUtils.hasText(title) ? title.trim() : file.getOriginalFilename();
            String extracted = documentTextExtractor.extract(file);
            AiDocument document = aiDocumentService.saveForCourse(file, courseId, name, chapter,
                    sourceType, sourceUrl, license, sourceAuthor, attribution, extracted);
            return Result.success(toVO(document));
        } catch (java.io.IOException exception) {
            throw new BusinessException(400, exception.getMessage());
        }
    }

    private void validateCourseResourceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "课程资源不能为空");
        if (file.getSize() > MAX_COURSE_RESOURCE_SIZE) {
            throw new BusinessException(400, "课程资源不能超过 50MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!List.of("pdf", "docx", "pptx", "txt", "md").contains(extension)) {
            throw new BusinessException(400, "课程资源仅支持 PDF、DOCX、PPTX、TXT、MD");
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<AiKnowledgeResourceVO>> list(@RequestParam Long courseId) {
        return Result.success(aiDocumentService.listCourseDocuments(courseId).stream().map(this::toVO).toList());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public Result<AiKnowledgeResourceVO> review(@PathVariable Long id,
                                                 @RequestParam String action,
                                                 @RequestParam(required = false) String remark) {
        return Result.success(toVO(aiDocumentService.reviewCourseDocument(id, action, remark)));
    }

    @PostMapping("/{id}/reindex")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> reindex(@PathVariable Long id) {
        return Result.success(aiDocumentService.reindexCourseDocument(id));
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        AiDocument document = aiDocumentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "课程资源不存在"));
        aiDocumentService.requireCourseAccess(document.getCourseId());
        if (!"READY".equals(document.getStatus())
                && !aiDocumentService.isCourseEditor(document.getCourseId())) {
            throw new BusinessException(403, "该课程资源尚未审核发布");
        }
        try {
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            String relative = document.getFileUrl().replaceFirst("^/uploads/", "");
            Path target = base.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
            if (!target.startsWith(base) || !java.nio.file.Files.isRegularFile(target)) return ResponseEntity.notFound().build();
            Resource resource = new UrlResource(target.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(target.getFileName().toString())
                    .orElseGet(() -> {
                        try {
                            return StringUtils.hasText(document.getContentType())
                                    ? MediaType.parseMediaType(document.getContentType())
                                    : MediaType.APPLICATION_OCTET_STREAM;
                        } catch (IllegalArgumentException ignored) {
                            return MediaType.APPLICATION_OCTET_STREAM;
                        }
                    });
            return ResponseEntity.ok().contentType(mediaType).body(resource);
        } catch (Exception exception) {
            throw new BusinessException(404, "课程资源文件不存在");
        }
    }

    private AiKnowledgeResourceVO toVO(AiDocument item) {
        AiKnowledgeResourceVO vo = new AiKnowledgeResourceVO();
        vo.setId(item.getId()); vo.setCourseId(item.getCourseId()); vo.setDocumentName(item.getDocumentName());
        vo.setChapter(item.getChapter()); vo.setSourceType(item.getSourceType()); vo.setSourceUrl(item.getSourceUrl());
        vo.setLicense(item.getLicense()); vo.setSourceAuthor(item.getSourceAuthor()); vo.setAttribution(item.getAttribution());
        vo.setFileUrl(item.getFileUrl()); vo.setFileSize(item.getFileSize()); vo.setTextLength(item.getTextLength());
        vo.setChunkCount(item.getChunkCount()); vo.setStatus(item.getStatus());
        vo.setCoursewareId(item.getCoursewareId()); vo.setIndexMode(item.getIndexMode());
        vo.setReviewRemark(item.getReviewRemark()); vo.setReviewedBy(item.getReviewedBy()); vo.setReviewedAt(item.getReviewedAt());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }
}
