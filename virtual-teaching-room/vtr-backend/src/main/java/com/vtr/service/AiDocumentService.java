package com.vtr.service;

import com.vtr.common.exception.BusinessException;
import com.vtr.entity.AiDocument;
import com.vtr.entity.AiDocumentChunk;
import com.vtr.entity.Courseware;
import com.vtr.repository.AiDocumentChunkRepository;
import com.vtr.repository.AiDocumentRepository;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.vo.AiSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 负责保存上传文档，并生成后续 RAG 可直接使用的文本片段。 */
@Service
@RequiredArgsConstructor
public class AiDocumentService {

    private static final int MAX_TEXT_LENGTH = 500_000;
    private static final int CHUNK_SIZE = 1_000;
    private static final int CHUNK_OVERLAP = 150;
    private static final int MIN_BOUNDARY_DISTANCE = 400;
    private static final int MAX_RETRIEVED_CHUNKS = 6;
    private static final int MAX_RETRIEVED_CHARS = 12_000;
    private static final int MAX_SEARCH_TERMS = 96;
    private static final int MIN_RELEVANT_SCORE = 6;
    private static final double MIN_RELEVANCE_RATIO = 0.35;
    private static final int TF_IDF_SCORE_SCALE = 36;
    private static final int EMBEDDING_SCORE_SCALE = 48;
    private static final double MIN_EMBEDDING_SIMILARITY = 0.35D;
    private static final String NO_RELEVANT_CONTEXT =
            "[系统提示：未检索到与当前问题直接相关的文档片段。]";
    private static final Pattern SEARCH_TOKEN_PATTERN =
            Pattern.compile("[a-zA-Z0-9_\\u4e00-\\u9fff]+");
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "请", "请分析", "分析", "这份", "文档", "内容", "提取", "重点", "给出", "清晰",
            "学习", "讲解", "一下", "什么", "如何", "怎样", "为什么", "帮我", "告诉我",
            "介绍", "提及", "是否", "没有", "如果", "明确", "说明", "涉及", "相关", "回答", "根据",
            "能否", "判断", "并", "和", "以及", "的", "是", "了", "在", "中", "对", "将", "从", "这", "个",
            "问题", "题目", "选项", "理由", "结论", "最终", "答案", "通常", "分别", "关于", "其中", "这个",
            "题", "可否", "能不能", "请问"
    );
    /** 这些短语只用于检索式清洗，不会修改用户原问题。 */
    private static final List<String> QUERY_NOISE_PHRASES = List.of(
            "逐项分析每个选项", "请逐项分析", "只根据这份文档", "根据这份文档", "请分析这份文档",
            "文档中是否介绍了", "文档中是否涉及", "文档中关于", "文档中", "这份文档", "这个题",
            "请解释", "请说明", "帮我解释", "帮我说明", "分别解决什么", "说明理由", "给出最终答案",
            "最终答案", "正确答案", "什么是", "为什么", "如何", "怎样", "请问", "请", "分析",
            "解释", "说明", "介绍", "提取", "重点", "讲解", "告诉我", "帮我", "根据", "回答",
            "是否", "没有", "涉及", "相关", "内容", "问题", "题目", "选项", "答案", "和", "以及"
    );
    private final AiDocumentRepository aiDocumentRepository;
    private final AiDocumentChunkRepository aiDocumentChunkRepository;
    private final CourseRepository courseRepository;
    private final CoursewareRepository coursewareRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final AiEmbeddingService aiEmbeddingService;

    @Value("${upload.path:./uploads/}")
    private String uploadPath;

    /**
     * 保存当前登录用户的文档和文本片段。
     * ownerId 不从前端参数读取，避免用户之间发生文档串读。
     */
    @Transactional
    public AiDocument saveForCurrentUser(MultipartFile file, String documentName, String extractedText) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        if (ownerId == null) {
            throw new BusinessException(401, "请先登录后再保存文档");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文档不能为空");
        }
        if (!StringUtils.hasText(extractedText)) {
            throw new BusinessException(400, "文档中没有可保存的文字");
        }
        if (extractedText.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(400, "文档内容超过当前保存上限");
        }

        List<ChunkRange> ranges = splitIntoRanges(extractedText);
        if (ranges.isEmpty()) {
            throw new BusinessException(400, "文档中没有可分段的文字");
        }

        AiDocument document = new AiDocument();
        document.setOwnerId(ownerId);
        document.setDocumentName(normalizeFilename(documentName));
        document.setFileExtension(extensionOf(documentName));
        document.setContentType(normalizeContentType(file.getContentType()));
        document.setFileSize(file.getSize());
        document.setExtractedText(extractedText);
        document.setTextLength(extractedText.length());
        document.setChunkCount(ranges.size());
        document.setStatus("READY");
        document.setIsDeleted(false);

        AiDocument savedDocument = aiDocumentRepository.save(document);

        boolean vectorReady = saveChunks(savedDocument, extractedText, ranges);
        savedDocument.setIndexMode(vectorReady ? "VECTOR_READY" : "TEXT_READY");
        savedDocument = aiDocumentRepository.save(savedDocument);
        return savedDocument;
    }

    /**
     * 将教师上传的课程资料保存为课程级知识库文档。
     * 原始文件、提取文本和切片均保存在本系统，不依赖外部网页。
     */
    @Transactional
    public AiDocument saveForCourse(MultipartFile file, Long courseId, String documentName,
                                    String chapter, String sourceType, String sourceUrl,
                                    String license, String sourceAuthor, String attribution,
                                    String extractedText) {
        Long ownerId = requireCourseEditor(courseId);
        if (file == null || file.isEmpty()) throw new BusinessException(400, "课程资源不能为空");
        if (!StringUtils.hasText(extractedText)) throw new BusinessException(400, "课程资源中没有可保存的文字");
        if (extractedText.length() > MAX_TEXT_LENGTH) throw new BusinessException(400, "课程资源内容超过当前保存上限");
        List<ChunkRange> ranges = splitIntoRanges(extractedText);
        if (ranges.isEmpty()) throw new BusinessException(400, "课程资源没有可分段的文字");

        AiDocument document = new AiDocument();
        document.setOwnerId(ownerId);
        document.setCourseId(courseId);
        document.setChapter(trimToNull(chapter));
        document.setSourceType(hasText(sourceType) ? sourceType.trim() : "OPEN_RESOURCE");
        document.setSourceUrl(trimToNull(sourceUrl));
        document.setLicense(trimToNull(license));
        document.setSourceAuthor(trimToNull(sourceAuthor));
        document.setAttribution(trimToNull(attribution));
        document.setDocumentName(normalizeFilename(documentName));
        document.setFileExtension(extensionOf(documentName));
        document.setContentType(normalizeContentType(file.getContentType()));
        document.setFileSize(file.getSize());
        document.setExtractedText(extractedText);
        document.setTextLength(extractedText.length());
        document.setChunkCount(ranges.size());
        // 课程资源必须经过教师团队审核后，才允许学生检索，避免未审核内容污染 RAG。
        document.setStatus("PENDING_REVIEW");
        document.setIsDeleted(false);
        document.setFileUrl(storeCourseFile(file, courseId, document.getDocumentName()));

        AiDocument savedDocument = aiDocumentRepository.save(document);
        boolean vectorReady = saveChunks(savedDocument, extractedText, ranges);
        savedDocument.setIndexMode(vectorReady ? "VECTOR_READY" : "TEXT_READY");
        savedDocument = aiDocumentRepository.save(savedDocument);
        return savedDocument;
    }

    /**
     * 将普通课程课件同步到 AI 文档索引。该方法不依赖当前登录线程，专供上传事件监听器调用。
     * 课件审核状态与 AI 文档可见状态分离：先完成索引，审核通过后才进入学生检索集合。
     */
    @Transactional
    public AiDocument indexCourseware(Courseware courseware, String extractedText, String indexMode) {
        if (courseware == null || courseware.getId() == null) {
            throw new BusinessException(400, "课件信息不完整，无法建立 AI 索引");
        }
        String text = extractedText == null ? "" : extractedText.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(400, "课件没有可建立索引的文字");
        }

        AiDocument document = aiDocumentRepository
                .findFirstByCoursewareIdAndIsDeletedFalseOrderByIdDesc(courseware.getId())
                .orElseGet(AiDocument::new);
        List<AiDocumentChunk> oldChunks = document.getId() == null
                ? List.of()
                : aiDocumentChunkRepository.findByDocumentId(document.getId());
        if (!oldChunks.isEmpty()) {
            aiDocumentChunkRepository.deleteAllInBatch(oldChunks);
            aiDocumentChunkRepository.flush();
        }

        List<ChunkRange> ranges = splitIntoRanges(text);
        if (ranges.isEmpty()) throw new BusinessException(400, "课件文本没有可分段内容");
        document.setOwnerId(courseware.getTeacherId());
        document.setCourseId(courseware.getCourseId());
        document.setCoursewareId(courseware.getId());
        document.setChapter(trimToNull(courseware.getChapter()));
        document.setSourceType("COURSEWARE_AUTO_INDEX");
        document.setFileUrl(courseware.getFileUrl());
        document.setDocumentName(normalizeFilename(courseware.getFileName()));
        document.setFileExtension(extensionOf(courseware.getFileName()));
        document.setContentType(normalizeContentType(contentTypeOf(courseware.getFileType())));
        document.setFileSize(courseware.getFileSize() == null ? 0L : courseware.getFileSize());
        document.setExtractedText(text);
        document.setTextLength(text.length());
        document.setChunkCount(ranges.size());
        document.setStatus(coursewarePublicationStatus(courseware.getStatus()));
        document.setReviewRemark(null);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        document.setIndexMode(StringUtils.hasText(indexMode) ? indexMode : "TEXT_READY");
        document.setIsDeleted(false);

        AiDocument saved = aiDocumentRepository.save(document);
        boolean vectorReady = saveChunks(saved, text, ranges);
        saved.setIndexMode(vectorReady ? "VECTOR_READY" : document.getIndexMode());
        saved = aiDocumentRepository.save(saved);

        courseware.setAiDocumentId(saved.getId());
        courseware.setAiIndexStatus(vectorReady ? "READY" : normalizeCoursewareIndexStatus(indexMode));
        courseware.setAiIndexMessage(vectorReady
                ? "文本已切片并完成 Embedding 向量入库"
                : "文本已切片入库，当前未完成 Embedding，暂使用本地关键词/TF-IDF 检索");
        courseware.setAiIndexedAt(LocalDateTime.now());
        coursewareRepository.save(courseware);
        return saved;
    }

    /** 只同步课件审核状态，不重复读取文件和调用 Embedding。 */
    @Transactional
    public void syncCoursewarePublication(Courseware courseware) {
        if (courseware == null || courseware.getId() == null) return;
        AiDocument document = aiDocumentRepository
                .findFirstByCoursewareIdAndIsDeletedFalseOrderByIdDesc(courseware.getId())
                .orElse(null);
        if (document == null) return;
        if ("DELETED".equalsIgnoreCase(courseware.getStatus())) {
            document.setIsDeleted(true);
            document.setStatus("REJECTED");
            document.setIndexMode("DELETED");
            aiDocumentRepository.save(document);
            return;
        }
        document.setStatus(coursewarePublicationStatus(courseware.getStatus()));
        document.setReviewRemark(courseware.getAuditRemark());
        document.setReviewedBy(courseware.getReviewedBy());
        document.setReviewedAt(courseware.getReviewedAt());
        aiDocumentRepository.save(document);
        courseware.setAiDocumentId(document.getId());
        if ("ACTIVE".equalsIgnoreCase(courseware.getStatus())) {
            courseware.setAiIndexStatus("VECTOR_READY".equals(document.getIndexMode())
                    ? "READY" : StringUtils.hasText(document.getIndexMode()) ? document.getIndexMode() : "TEXT_READY");
            courseware.setAiIndexMessage("课件审核通过，已纳入课程 AI 检索");
        } else if ("PENDING".equalsIgnoreCase(courseware.getStatus())) {
            courseware.setAiIndexStatus("VECTOR_READY".equals(document.getIndexMode()) ? "READY" : "TEXT_READY");
            courseware.setAiIndexMessage("索引已完成，等待课件审核通过后供学生检索");
        } else {
            courseware.setAiIndexStatus("SUSPENDED");
            courseware.setAiIndexMessage("课件当前未发布，暂不参与学生 AI 检索");
        }
        coursewareRepository.save(courseware);
    }

    @Transactional
    public void markCoursewareIndexStatus(Courseware courseware, String status, String message) {
        if (courseware == null) return;
        courseware.setAiIndexStatus(status);
        courseware.setAiIndexMessage(trimToNull(message));
        courseware.setAiIndexedAt(LocalDateTime.now());
        coursewareRepository.save(courseware);
    }

    @Transactional
    public void markCoursewareIndexFailed(Courseware courseware, String message) {
        if (courseware == null || courseware.getId() == null) return;
        AiDocument document = aiDocumentRepository
                .findFirstByCoursewareIdAndIsDeletedFalseOrderByIdDesc(courseware.getId())
                .orElseGet(AiDocument::new);
        document.setOwnerId(courseware.getTeacherId());
        document.setCourseId(courseware.getCourseId());
        document.setCoursewareId(courseware.getId());
        document.setChapter(trimToNull(courseware.getChapter()));
        document.setSourceType("COURSEWARE_AUTO_INDEX");
        document.setFileUrl(courseware.getFileUrl());
        document.setDocumentName(normalizeFilename(courseware.getFileName()));
        document.setFileExtension(extensionOf(courseware.getFileName()));
        document.setContentType(contentTypeOf(courseware.getFileType()));
        document.setFileSize(courseware.getFileSize() == null ? 0L : courseware.getFileSize());
        document.setExtractedText("");
        document.setTextLength(0);
        document.setChunkCount(0);
        document.setStatus(coursewarePublicationStatus(courseware.getStatus()));
        document.setIndexMode("FAILED");
        document.setIsDeleted(false);
        AiDocument saved = aiDocumentRepository.save(document);
        courseware.setAiDocumentId(saved.getId());
        courseware.setAiIndexStatus("FAILED");
        courseware.setAiIndexMessage(trimToNull(message));
        courseware.setAiIndexedAt(LocalDateTime.now());
        coursewareRepository.save(courseware);
    }

    @Transactional
    public void markCoursewareDeleted(Long coursewareId) {
        if (coursewareId == null) return;
        aiDocumentRepository.findFirstByCoursewareIdAndIsDeletedFalseOrderByIdDesc(coursewareId)
                .ifPresent(document -> {
                    document.setIsDeleted(true);
                    document.setStatus("REJECTED");
                    document.setIndexMode("DELETED");
                    aiDocumentRepository.save(document);
                });
    }

    @Transactional(readOnly = true)
    public List<AiDocument> listCourseDocuments(Long courseId) {
        requireCourseAccess(courseId);
        if (isCourseEditor(courseId)) {
            return aiDocumentRepository.findByCourseIdAndIsDeletedFalseOrderByCreatedAtDesc(courseId);
        }
        return aiDocumentRepository.findByCourseIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(courseId, "READY");
    }

    @Transactional
    public AiDocument reviewCourseDocument(Long documentId, String action, String remark) {
        AiDocument document = aiDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(404, "课程资源不存在"));
        if (document.getCourseId() == null) {
            throw new BusinessException(400, "个人文档不能进行课程资源审核");
        }
        requireCourseEditor(document.getCourseId());
        if (!"PENDING_REVIEW".equals(document.getStatus())) {
            throw new BusinessException(409, "该课程资源已经审核过，不能重复审核");
        }
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(normalizedAction) && !"REJECT".equals(normalizedAction)) {
            throw new BusinessException(400, "审核操作无效");
        }
        document.setStatus("APPROVE".equals(normalizedAction) ? "READY" : "REJECTED");
        document.setReviewRemark(trimToNull(remark));
        document.setReviewedBy(SecurityUtils.getCurrentUserId());
        document.setReviewedAt(java.time.LocalDateTime.now());
        return aiDocumentRepository.save(document);
    }

    /** 为已有课程资源补建 Embedding；未配置向量服务时明确提示教师，而不是静默成功。 */
    @Transactional
    public int reindexCourseDocument(Long documentId) {
        AiDocument document = aiDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(404, "课程资源不存在"));
        if (document.getCourseId() == null) throw new BusinessException(400, "个人文档不能重建课程索引");
        requireCourseEditor(document.getCourseId());
        if (!aiEmbeddingService.isAvailable()) {
            throw new BusinessException(400, "尚未配置可用的 Embedding 服务，请先配置 AI_EMBEDDING_* 参数");
        }
        List<AiDocumentChunk> chunks = aiDocumentChunkRepository.findByDocumentId(documentId);
        if (chunks.isEmpty()) throw new BusinessException(404, "课程资源没有可重建的片段");
        List<List<Double>> embeddings = aiEmbeddingService.embedBatch(
                chunks.stream().map(AiDocumentChunk::getContent).toList());
        if (embeddings.size() != chunks.size()) {
            throw new BusinessException(502, "Embedding 服务未返回完整向量，索引未更新");
        }
        for (int index = 0; index < chunks.size(); index++) {
            chunks.get(index).setEmbeddingJson(aiEmbeddingService.toJson(embeddings.get(index)));
        }
        aiDocumentChunkRepository.saveAll(chunks);
        document.setIndexMode("VECTOR_READY");
        aiDocumentRepository.save(document);
        if (document.getCoursewareId() != null) {
            coursewareRepository.findById(document.getCoursewareId()).ifPresent(courseware -> {
                courseware.setAiIndexStatus("READY");
                courseware.setAiIndexMessage("文本已切片并完成 Embedding 向量入库");
                courseware.setAiIndexedAt(LocalDateTime.now());
                coursewareRepository.save(courseware);
            });
        }
        return chunks.size();
    }

    @Transactional(readOnly = true)
    public RetrievedDocumentContext retrieveForCourse(Long courseId, String chapter, String query) {
        return retrieveForCourse(courseId, chapter, query, false);
    }

    /**
     * Agent 任务通常是“生成备课方案/分析学情”这类泛化指令，指令本身没有教材关键词。
     * 这类任务在关键词未命中时使用章节开头片段作为任务上下文；普通学生问答仍保持严格命中，
     * 避免把无关教材内容误当成答案依据。
     */
    @Transactional(readOnly = true)
    public RetrievedDocumentContext retrieveForCourse(Long courseId, String chapter, String query,
                                                      boolean fallbackToCourseOverview) {
        requireCourseAccess(courseId);
        List<AiDocumentChunk> chunks = aiDocumentChunkRepository.findByCourseIdAndChapter(
                courseId, hasText(chapter) ? chapter.trim() : null);
        if (chunks.isEmpty()) {
            return new RetrievedDocumentContext("课程知识库", NO_RELEVANT_CONTEXT, 0, List.of(), "NONE");
        }
        List<AiDocumentChunk> selected = selectRelevantChunks(chunks, query);
        if (selected.isEmpty() && fallbackToCourseOverview) {
            selected = chunks.stream().limit(MAX_RETRIEVED_CHUNKS).toList();
        }
        if (selected.isEmpty()) {
            return new RetrievedDocumentContext("课程知识库", NO_RELEVANT_CONTEXT, 0, List.of(), "NONE");
        }
        return new RetrievedDocumentContext("课程知识库", formatCourseContext(selected), selected.size(),
                buildSources(selected), retrievalMode(selected));
    }

    public void requireCourseAccess(Long courseId) {
        if (courseId == null) throw new BusinessException(400, "课程不能为空");
        if (!courseRepository.existsById(courseId)) throw new BusinessException(404, "课程不存在");
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(401, "请先登录");
        if (SecurityUtils.isAdmin()) return;
        boolean editor = courseRepository.findById(courseId).map(c -> userId.equals(c.getCreatedBy())).orElse(false)
                || courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .map(m -> "ACTIVE".equals(m.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(m.getRole()))
                .orElse(false);
        if (editor) return;
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus())).map(c -> c.getId()).toList();
        if (classroomIds.isEmpty() || !classroomStudentRelationRepository
                .existsByClassroomIdInAndStudentIdAndStatus(classroomIds, userId, "ACTIVE")) {
            throw new BusinessException(403, "没有访问该课程知识库的权限");
        }
    }

    public boolean isCourseEditor(Long courseId) {
        if (courseId == null) return false;
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        return courseRepository.findById(courseId).map(c -> userId.equals(c.getCreatedBy())).orElse(false)
                || courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .map(m -> "ACTIVE".equals(m.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(m.getRole()))
                .orElse(false);
    }

    public Long requireCourseEditor(Long courseId) {
        requireCourseAccess(courseId);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin()
                && !courseRepository.findById(courseId).map(c -> userId.equals(c.getCreatedBy())).orElse(false)
                && !courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .map(m -> "ACTIVE".equals(m.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(m.getRole()))
                .orElse(false)) {
            throw new BusinessException(403, "只有课程教师团队可以上传课程资源");
        }
        return userId;
    }

    private boolean saveChunks(AiDocument document, String text, List<ChunkRange> ranges) {
        List<AiDocumentChunk> chunks = new ArrayList<>(ranges.size());
        List<String> contents = new ArrayList<>(ranges.size());
        for (int index = 0; index < ranges.size(); index++) {
            ChunkRange range = ranges.get(index);
            AiDocumentChunk chunk = new AiDocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(index);
            chunk.setContent(text.substring(range.start(), range.end()));
            chunk.setHeading(extractHeading(chunk.getContent(), document.getDocumentName()));
            chunk.setCharStart(range.start());
            chunk.setCharEnd(range.end());
            chunks.add(chunk);
            contents.add(chunk.getContent());
        }
        List<List<Double>> embeddings = aiEmbeddingService.embedBatch(contents);
        boolean vectorReady = embeddings.size() == chunks.size();
        if (vectorReady) {
            for (int index = 0; index < chunks.size(); index++) {
                chunks.get(index).setEmbeddingJson(aiEmbeddingService.toJson(embeddings.get(index)));
            }
        }
        aiDocumentChunkRepository.saveAll(chunks);
        return vectorReady;
    }

    private String coursewarePublicationStatus(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) return "READY";
        if ("PENDING".equalsIgnoreCase(status)) return "PENDING_REVIEW";
        return "REJECTED";
    }

    private String normalizeCoursewareIndexStatus(String indexMode) {
        if ("METADATA_ONLY".equalsIgnoreCase(indexMode)) return "METADATA_ONLY";
        if ("WAITING_TRANSCRIPT".equalsIgnoreCase(indexMode)) return "WAITING_TRANSCRIPT";
        return "TEXT_READY";
    }

    private String contentTypeOf(String fileType) {
        if (!StringUtils.hasText(fileType)) return "application/octet-stream";
        return switch (fileType.trim().toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt", "pptx" -> "application/vnd.ms-powerpoint";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "txt", "md" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String storeCourseFile(MultipartFile file, Long courseId, String documentName) {
        try {
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path directory = base.resolve("course-resources").resolve(String.valueOf(courseId)).normalize();
            Files.createDirectories(directory);
            String safeName = documentName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
            String sourceExtension = extensionOf(file.getOriginalFilename());
            if (!safeName.contains(".") && hasText(sourceExtension)) {
                safeName = safeName + "." + sourceExtension;
            }
            Path target = directory.resolve(UUID.randomUUID() + "_" + safeName).normalize();
            if (!target.startsWith(base)) throw new BusinessException(400, "资源文件路径无效");
            file.transferTo(target.toFile());
            return "/uploads/course-resources/" + courseId + "/" + target.getFileName();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("课程资源保存失败", exception);
        }
    }

    private String formatCourseContext(List<AiDocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (AiDocumentChunk chunk : chunks) {
            AiDocument document = chunk.getDocument();
            String block = "[课程资源：" + document.getDocumentName()
                    + (hasText(document.getChapter()) ? "；章节：" + document.getChapter() : "")
                    + (hasText(chunk.getHeading()) ? "；小节：" + chunk.getHeading() : "")
                    + "；片段 " + (chunk.getChunkIndex() + 1) + "]\n"
                    + chunk.getContent() + "\n\n";
            if (context.length() + block.length() <= MAX_RETRIEVED_CHARS) context.append(block);
        }
        return context.toString().trim();
    }

    private boolean hasText(String value) { return StringUtils.hasText(value); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }

    /**
     * 按当前用户和文档 ID 检索与问题最相关的文本片段。
     * 当前使用轻量级关键词评分，后续可以在不改变接口的前提下替换为向量检索。
     */
    @Transactional(readOnly = true)
    public RetrievedDocumentContext retrieveForCurrentUser(Long documentId, String query) {
        return retrieveForCurrentUser(documentId, query, false);
    }

    /**
     * 首次上传文档时，如果用户只说“请分析这份文档”，关键词可能不足以召回片段；
     * 此时允许使用文档开头的少量片段作为概览上下文，避免首次上传后模型实际上没有读到文档。
     */
    @Transactional(readOnly = true)
    public RetrievedDocumentContext retrieveForCurrentUser(Long documentId, String query,
                                                           boolean fallbackToDocumentOverview) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        if (ownerId == null) {
            throw new BusinessException(401, "请先登录后再使用文档问答");
        }

        AiDocument document = aiDocumentRepository.findByIdAndOwnerIdAndIsDeletedFalse(documentId, ownerId)
                .orElseThrow(() -> new BusinessException(404, "找不到当前用户的文档"));
        List<AiDocumentChunk> chunks = aiDocumentChunkRepository.findByDocumentIdAndOwnerId(documentId, ownerId);
        if (chunks.isEmpty()) {
            throw new BusinessException(404, "该文档没有可检索的内容");
        }

        List<AiDocumentChunk> selected = selectRelevantChunks(chunks, query);
        if (selected.isEmpty() && fallbackToDocumentOverview) {
            selected = chunks.stream().limit(MAX_RETRIEVED_CHUNKS).toList();
        }
        if (selected.isEmpty()) {
            return new RetrievedDocumentContext(document.getDocumentName(), NO_RELEVANT_CONTEXT, 0, List.of(), "NONE");
        }
        String context = formatRetrievedContext(selected);
        return new RetrievedDocumentContext(document.getDocumentName(), context, selected.size(),
                buildSources(selected), retrievalMode(selected));
    }

    private List<ChunkRange> splitIntoRanges(String text) {
        List<ChunkRange> ranges = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int hardEnd = Math.min(start + CHUNK_SIZE, text.length());
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int boundary = findBoundary(text, start, hardEnd);
                if (boundary > start) {
                    end = boundary;
                }
            }

            ranges.add(new ChunkRange(start, end));
            if (end >= text.length()) {
                break;
            }

            int nextStart = Math.max(start + 1, end - CHUNK_OVERLAP);
            start = nextStart > start ? nextStart : end;
        }
        return ranges;
    }

    /** 在片段后半段优先寻找自然断点，找不到时使用固定长度。 */
    private int findBoundary(String text, int start, int hardEnd) {
        int lowerBound = Math.min(hardEnd, start + MIN_BOUNDARY_DISTANCE);
        for (int index = hardEnd - 1; index >= lowerBound; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '；' || current == '.' || current == '!' || current == '?'
                    || current == ';') {
                return index + 1;
            }
        }
        return hardEnd;
    }

    private List<AiDocumentChunk> selectRelevantChunks(List<AiDocumentChunk> chunks, String query) {
        List<SearchTerm> terms = searchTerms(query);
        List<Double> queryEmbedding = aiEmbeddingService.embed(query);

        String normalizedQuery = normalizeSearchText(removeQueryNoise(query));
        Map<String, Integer> documentFrequency = documentFrequency(chunks, terms);
        List<ScoredChunk> scored = chunks.stream()
                .map(chunk -> {
                    int keywordScore = scoreChunk(chunk, terms, normalizedQuery);
                    double similarity = tfIdfSimilarity(chunk, terms, documentFrequency, chunks.size());
                    double embeddingSimilarity = aiEmbeddingService.cosineSimilarity(
                            queryEmbedding, aiEmbeddingService.fromJson(chunk.getEmbeddingJson()));
                    int hybridScore = keywordScore
                            + (int) Math.round(similarity * TF_IDF_SCORE_SCALE)
                            + (int) Math.round(Math.max(0D, embeddingSimilarity) * EMBEDDING_SCORE_SCALE);
                    return new ScoredChunk(chunk, hybridScore, similarity, embeddingSimilarity);
                })
                .filter(item -> item.score() > 0 || item.embeddingSimilarity() >= MIN_EMBEDDING_SIMILARITY)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(ScoredChunk::embeddingSimilarity, Comparator.reverseOrder())
                        .thenComparing(ScoredChunk::similarity, Comparator.reverseOrder())
                        .thenComparing(item -> item.chunk().getChunkIndex()))
                .toList();

        if (scored.isEmpty()) {
            return List.of();
        }
        int bestScore = scored.get(0).score();
        int minimumScore = Math.max(MIN_RELEVANT_SCORE,
                (int) Math.ceil(bestScore * MIN_RELEVANCE_RATIO));
        List<ScoredChunk> relevant = scored.stream()
                .filter(item -> item.score() >= minimumScore)
                .toList();

        // 多份课程资料同时命中时，最多取每份资料 4 个片段，避免一本大教材完全压住教师补充材料。
        Map<Long, Integer> documentCounts = new HashMap<>();
        List<AiDocumentChunk> selected = new ArrayList<>();
        for (ScoredChunk item : relevant) {
            Long documentId = item.chunk().getDocument().getId();
            int count = documentCounts.getOrDefault(documentId, 0);
            if (count >= 4 && relevant.size() > 4) continue;
            selected.add(item.chunk());
            documentCounts.put(documentId, count + 1);
            if (selected.size() >= MAX_RETRIEVED_CHUNKS) break;
        }
        return selected.stream()
                .sorted(Comparator.comparing(AiDocumentChunk::getChunkIndex))
                .toList();
    }

    private String retrievalMode(List<AiDocumentChunk> chunks) {
        boolean hasEmbedding = aiEmbeddingService.isAvailable()
                && chunks != null
                && chunks.stream().anyMatch(chunk -> hasText(chunk.getEmbeddingJson()));
        return hasEmbedding ? "HYBRID_KEYWORD_TFIDF_EMBEDDING" : "HYBRID_KEYWORD_TFIDF";
    }

    private int scoreChunk(AiDocumentChunk chunk, List<SearchTerm> terms, String normalizedQuery) {
        String content = chunk.getContent();
        String normalizedContent = normalizeSearchText(content);
        AiDocument document = chunk.getDocument();
        String normalizedTitle = normalizeSearchText(document == null ? "" : document.getDocumentName());
        String normalizedChapter = normalizeSearchText(document == null ? "" : document.getChapter());
        int score = 0;
        if (normalizedQuery.length() >= 4 && normalizedContent.contains(normalizedQuery)) {
            score += 28;
        }
        if (normalizedQuery.length() >= 2 && normalizedTitle.contains(normalizedQuery)) {
            score += 24;
        }
        if (normalizedQuery.length() >= 2 && normalizedChapter.contains(normalizedQuery)) {
            score += 20;
        }
        for (SearchTerm term : terms) {
            int contentOccurrences = countOccurrences(normalizedContent, term.value());
            int titleOccurrences = countOccurrences(normalizedTitle, term.value());
            int chapterOccurrences = countOccurrences(normalizedChapter, term.value());
            score += Math.min(contentOccurrences, 3) * term.weight();
            score += Math.min(titleOccurrences, 2) * 15;
            score += Math.min(chapterOccurrences, 2) * 12;
        }
        if (terms.size() >= 2) {
            long coveredTerms = terms.stream()
                    .filter(term -> normalizedContent.contains(term.value())
                            || normalizedTitle.contains(term.value())
                            || normalizedChapter.contains(term.value()))
                    .count();
            if (coveredTerms == terms.size()) score += 18;
        }
        return score;
    }

    /**
     * 计算轻量 TF-IDF 相似度，作为关键词评分的第二路召回。
     *
     * <p>当前项目不强制依赖外部向量数据库，因此先用可解释、零新增服务的统计语义近似增强中文改写检索。
     * 后续接入真正 Embedding 服务时，可以保留本方法作为离线或降级检索。</p>
     */
    private double tfIdfSimilarity(AiDocumentChunk chunk, List<SearchTerm> terms,
                                   Map<String, Integer> documentFrequency, int documentCount) {
        String content = normalizeSearchText(chunk.getContent());
        if (!StringUtils.hasText(content)) return 0D;

        double queryNorm = 0D;
        double contentNorm = 0D;
        double dot = 0D;
        for (SearchTerm term : terms) {
            int df = documentFrequency.getOrDefault(term.value(), 0);
            double idf = Math.log((documentCount + 1D) / (df + 1D)) + 1D;
            double queryWeight = term.weight() * idf;
            int occurrences = countOccurrences(content, term.value());
            double contentWeight = Math.min(4, occurrences) * idf;
            queryNorm += queryWeight * queryWeight;
            contentNorm += contentWeight * contentWeight;
            dot += queryWeight * contentWeight;
        }
        if (queryNorm <= 0D || contentNorm <= 0D) return 0D;
        return dot / (Math.sqrt(queryNorm) * Math.sqrt(contentNorm));
    }

    private Map<String, Integer> documentFrequency(List<AiDocumentChunk> chunks, List<SearchTerm> terms) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (AiDocumentChunk chunk : chunks) {
            String content = normalizeSearchText(chunk.getContent());
            for (SearchTerm term : terms) {
                if (content.contains(term.value())) {
                    frequencies.merge(term.value(), 1, Integer::sum);
                }
            }
        }
        return frequencies;
    }

    private List<SearchTerm> searchTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        Map<String, Integer> weightedTerms = new HashMap<>();
        String queryCore = removeQueryNoise(query.toLowerCase(Locale.ROOT));
        Matcher matcher = SEARCH_TOKEN_PATTERN.matcher(queryCore);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches(".*[\\u4e00-\\u9fff].*")) {
                addChineseTerms(weightedTerms, token);
            } else if (token.length() >= 2 && !SEARCH_STOP_WORDS.contains(token)) {
                putTerm(weightedTerms, token, 18);
            }
        }
        return weightedTerms.entrySet().stream()
                .map(entry -> new SearchTerm(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(SearchTerm::weight).reversed()
                        .thenComparing(item -> item.value().length(), Comparator.reverseOrder()))
                .limit(MAX_SEARCH_TERMS)
                .toList();
    }

    /** 去掉提问模板后再提取关键词，避免“文档、问题、分析”等泛词污染检索结果。 */
    private String removeQueryNoise(String query) {
        String cleaned = query;
        for (String phrase : QUERY_NOISE_PHRASES) {
            cleaned = cleaned.replace(phrase, " ");
        }
        return cleaned;
    }

    /** 长术语优先，短 n-gram 只作为补充，兼顾中文未分词文本。 */
    private void addChineseTerms(Map<String, Integer> terms, String token) {
        if (token.length() >= 3 && !SEARCH_STOP_WORDS.contains(token)) {
            putTerm(terms, token, token.length() >= 4 ? 14 : 18);
        }
        for (int length = Math.min(4, token.length()); length >= 2; length--) {
            for (int index = 0; index <= token.length() - length; index++) {
                String phrase = token.substring(index, index + length);
                if (SEARCH_STOP_WORDS.contains(phrase)) {
                    continue;
                }
                int weight = length >= 4 ? 6 : length == 3 ? 5 : 2;
                putTerm(terms, phrase, weight);
            }
        }
        if (token.length() == 1 && !SEARCH_STOP_WORDS.contains(token)) {
            putTerm(terms, token, 4);
        }
    }

    private void putTerm(Map<String, Integer> terms, String term, int weight) {
        terms.merge(term, weight, Math::max);
    }

    private String normalizeSearchText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{P}\\p{S}]+", "");
    }

    private int countOccurrences(String text, String term) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(term)) {
            return 0;
        }
        int count = 0;
        int fromIndex = 0;
        while (fromIndex <= text.length() - term.length()) {
            int found = text.indexOf(term, fromIndex);
            if (found < 0) {
                break;
            }
            count++;
            fromIndex = found + term.length();
        }
        return count;
    }

    private String formatRetrievedContext(List<AiDocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (AiDocumentChunk chunk : chunks) {
            String block = "[文档片段 " + (chunk.getChunkIndex() + 1)
                    + (hasText(chunk.getHeading()) ? "；小节：" + chunk.getHeading() : "")
                    + "]\n" + chunk.getContent() + "\n\n";
            if (context.length() + block.length() <= MAX_RETRIEVED_CHARS) {
                context.append(block);
                continue;
            }
            int remaining = MAX_RETRIEVED_CHARS - context.length();
            if (remaining > 30) {
                context.append(block, 0, remaining);
            }
            break;
        }
        return context.toString().trim();
    }

    private List<AiSourceVO> buildSources(List<AiDocumentChunk> chunks) {
        return chunks.stream().map(chunk -> {
            AiDocument document = chunk.getDocument();
            AiSourceVO source = new AiSourceVO(
                    chunk.getChunkIndex() + 1,
                    chunk.getCharStart(),
                    chunk.getCharEnd(),
                    excerptOf(chunk.getContent()),
                    document.getId(),
                    document.getDocumentName(),
                    document.getChapter());
            source.setHeading(chunk.getHeading());
            source.setSourceType(document.getSourceType());
            source.setSourceUrl(document.getSourceUrl());
            source.setLicense(document.getLicense());
            source.setSourceAuthor(document.getSourceAuthor());
            source.setAttribution(document.getAttribution());
            source.setSourceKind(document.getCourseId() == null ? "DOCUMENT" : "COURSE");
            return source;
        }).toList();
    }

    private String extractHeading(String content, String fallbackName) {
        if (!StringUtils.hasText(content)) return trimToNull(fallbackName);
        String[] lines = content.replace('\r', '\n').split("\\n+");
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim()
                    .replaceFirst("^#+\\s*", "")
                    .replaceFirst("^\\[[^]]+页]\\s*", "")
                    .trim();
            if (!StringUtils.hasText(candidate) || candidate.length() > 120) continue;
            if (candidate.matches("^(第[一二三四五六七八九十百千万0-9]+[章节部分]|[0-9]+(?:\\.[0-9]+)*[、.：:]?|[一二三四五六七八九十百千万]+[、.：:]).*")) {
                return candidate;
            }
        }
        return null;
    }

    private String excerptOf(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        int maxLength = 180;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "…";
    }

    private String normalizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "上传文档";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String safeName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!StringUtils.hasText(safeName)) {
            return "上传文档";
        }
        return safeName.length() <= 255 ? safeName : safeName.substring(0, 255);
    }

    private String extensionOf(String filename) {
        String safeName = normalizeFilename(filename);
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            return "";
        }
        String extension = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.length() <= 20 ? extension : extension.substring(0, 20);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        return contentType.length() <= 100 ? contentType : contentType.substring(0, 100);
    }

    private record ChunkRange(int start, int end) {
    }

    private record SearchTerm(String value, int weight) {
    }

    private record ScoredChunk(AiDocumentChunk chunk, int score, double similarity,
                               double embeddingSimilarity) {
    }

    public record RetrievedDocumentContext(String documentName, String content, int chunkCount,
                                           List<AiSourceVO> sources, String retrievalMode) {
        public RetrievedDocumentContext(String documentName, String content, int chunkCount,
                                        List<AiSourceVO> sources) {
            this(documentName, content, chunkCount, sources,
                    chunkCount > 0 ? "HYBRID_KEYWORD_TFIDF" : "NONE");
        }
    }
}
