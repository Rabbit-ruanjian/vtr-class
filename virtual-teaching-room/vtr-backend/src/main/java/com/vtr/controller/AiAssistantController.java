package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AiChatRequest;
import com.vtr.dto.AiFeedbackRequest;
import com.vtr.dto.WebSearchRequest;
import com.vtr.entity.AiDocument;
import com.vtr.service.AiDocumentService;
import com.vtr.service.AiConversationService;
import com.vtr.service.DocumentTextExtractor;
import com.vtr.service.KimiBuiltInWebSearchProvider;
import com.vtr.service.KimiAiService;
import com.vtr.vo.AiChatVO;
import com.vtr.vo.WebSearchCapabilityVO;
import com.vtr.vo.WebSearchResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.validation.Valid;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final KimiAiService kimiAiService;
    private final DocumentTextExtractor documentTextExtractor;
    private final AiDocumentService aiDocumentService;
    private final AiConversationService aiConversationService;
    private final KimiBuiltInWebSearchProvider kimiBuiltInWebSearchProvider;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<AiChatVO> chat(@RequestBody @Valid AiChatRequest request) {
        long startedAt = System.nanoTime();
        prepareRequest(request);
        aiConversationService.restoreHistory(request);
        AiDocumentService.RetrievedDocumentContext retrievedContext = enrichWithRetrievedDocument(request);
        AiChatVO response = kimiAiService.chat(request);
        if (retrievedContext != null) {
            response.setSources(retrievedContext.sources());
            response.setHasEvidence(retrievedContext.chunkCount() > 0);
            response.setRetrievalMode(retrievedContext.retrievalMode());
            response.setRetrievedChunkCount(retrievedContext.chunkCount());
        }
        finalizeResponse(request, response, startedAt);
        recordResponseSafely(request, response);
        return Result.success(response);
    }

    /** 课程教研 Agent 入口：任务类型受服务端白名单约束，知识只来自本地课程库。 */
    @PostMapping("/agent")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<AiChatVO> agent(@RequestBody @Valid AiChatRequest request) {
        long startedAt = System.nanoTime();
        prepareRequest(request);
        aiConversationService.restoreHistory(request);
        request.setAgentTask(normalizeAgentTask(request.getAgentTask()));
        if (request.getCourseId() != null && !"TUTOR".equals(request.getAgentTask())) {
            aiDocumentService.requireCourseEditor(request.getCourseId());
        }
        AiDocumentService.RetrievedDocumentContext retrievedContext = enrichWithRetrievedDocument(request);
        AiChatVO response = kimiAiService.chat(request);
        if (retrievedContext != null) {
            response.setSources(retrievedContext.sources());
            response.setHasEvidence(retrievedContext.chunkCount() > 0);
            response.setRetrievalMode(retrievedContext.retrievalMode());
            response.setRetrievedChunkCount(retrievedContext.chunkCount());
        }
        finalizeResponse(request, response, startedAt);
        recordResponseSafely(request, response);
        return Result.success(response);
    }

    @PostMapping("/feedback")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Void> feedback(@RequestBody @Valid AiFeedbackRequest request) {
        aiConversationService.saveFeedback(request);
        return Result.success();
    }

    /** 当前账号专属的 AI 历史记录，按时间倒序返回，绝不读取其他账号数据。 */
    @org.springframework.web.bind.annotation.GetMapping("/history")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<List<com.vtr.vo.AiConversationSummary>> history() {
        return Result.success(aiConversationService.recentForCurrentUser());
    }

    /** 清除当前账号的 AI 长期记忆；不会影响课程资料、作业或其他账号。 */
    @DeleteMapping("/history")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Void> clearHistory() {
        aiConversationService.clearForCurrentUser();
        return Result.success();
    }

    /** 删除当前账号侧边栏中的某一条对话（整段窗口）；只影响当前账号。 */
    @DeleteMapping("/history/{conversationId}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<Void> deleteConversation(
            @org.springframework.web.bind.annotation.PathVariable("conversationId") String conversationId) {
        aiConversationService.deleteForCurrentUser(conversationId);
        return Result.success();
    }

    @org.springframework.web.bind.annotation.GetMapping("/web-search/capability")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<WebSearchCapabilityVO> webSearchCapability() {
        return Result.success(kimiBuiltInWebSearchProvider.detectCapability());
    }

    /** 联网搜索统一入口，返回 Kimi 实际搜索摘要和可识别的来源链接。 */
    @PostMapping("/web-search")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<List<WebSearchResultVO>> webSearch(@RequestBody @Valid WebSearchRequest request) {
        return Result.success(kimiBuiltInWebSearchProvider.search(request.getQuery().trim()));
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<AiChatVO> document(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "message", defaultValue = "请分析这份文档，提取重点并给出清晰的学习讲解。") String message,
            @RequestParam(value = "contextTitle", required = false) String contextTitle,
            @RequestParam(value = "contextMeta", required = false) String contextMeta,
            @RequestParam(value = "contextExcerpt", required = false) String contextExcerpt,
            @RequestParam(value = "questionType", required = false) String questionType,
            @RequestParam(value = "questionId", required = false) Long questionId,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "chapter", required = false) String chapter,
            @RequestParam(value = "agentTask", required = false) String agentTask,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "history", required = false) String historyJson
    ) {
        long startedAt = System.nanoTime();
        try {
            AiChatRequest request = new AiChatRequest();
            request.setConversationId(conversationId);
            prepareRequest(request);
            request.setMessage(StringUtils.hasText(message)
                    ? message.trim()
                    : "请分析这份文档，提取重点并给出清晰的学习讲解。");
            request.setContextTitle(contextTitle);
            request.setContextMeta(contextMeta);
            request.setContextExcerpt(contextExcerpt);
            request.setQuestionType(questionType);
            request.setQuestionId(questionId);
            request.setCourseId(courseId);
            request.setChapter(chapter);
            request.setAgentTask(normalizeAgentTask(agentTask));
            if (courseId != null) {
                aiDocumentService.requireCourseAccess(courseId);
                if (!"TUTOR".equals(request.getAgentTask())) {
                    aiDocumentService.requireCourseEditor(courseId);
                }
            }
            request.setDocumentName(safeFilename(file.getOriginalFilename()));
            String extractedText = documentTextExtractor.extract(file);
            request.setDocumentText(extractedText);
            request.setHistory(parseHistory(historyJson));
            aiConversationService.restoreHistory(request);

            AiDocument savedDocument = aiDocumentService.saveForCurrentUser(
                    file, request.getDocumentName(), extractedText);
            // 首次上传文档也必须经过同一条 RAG 链路；不能把整篇长文直接塞给模型，
            // 也不能因为“请分析这份文档”缺少关键词就让模型实际上看不到正文。
            request.setDocumentId(savedDocument.getId());
            AiDocumentService.RetrievedDocumentContext retrievedContext = aiDocumentService
                    .retrieveForCurrentUser(savedDocument.getId(), buildRetrievalQuery(request), true);
            request.setDocumentName(retrievedContext.documentName());
            request.setRetrievedDocumentContext(retrievedContext.content());
            request.setDocumentText(null);
            AiChatVO response = kimiAiService.chat(request);
            response.setDocumentId(savedDocument.getId());
            response.setChunkCount(savedDocument.getChunkCount());
            response.setSources(retrievedContext.sources());
            response.setHasEvidence(retrievedContext.chunkCount() > 0);
            response.setRetrievalMode(retrievedContext.retrievalMode());
            response.setRetrievedChunkCount(retrievedContext.chunkCount());
            finalizeResponse(request, response, startedAt);
            recordResponseSafely(request, response);
            return Result.success(response);
        } catch (IOException exception) {
            throw new BusinessException(400, exception.getMessage());
        }
    }

    /**
     * AI 助手多模态入口：图片和视频先由后端接收，再按 Kimi 的多模态协议转发。
     * 这样前端不需要把大文件拼进 JSON，也能统一携带课程 RAG 上下文。
     */
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<AiChatVO> media(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "message", defaultValue = "请分析这个图片或视频，并结合学习场景给出清晰讲解。") String message,
            @RequestParam(value = "contextTitle", required = false) String contextTitle,
            @RequestParam(value = "contextMeta", required = false) String contextMeta,
            @RequestParam(value = "contextExcerpt", required = false) String contextExcerpt,
            @RequestParam(value = "questionType", required = false) String questionType,
            @RequestParam(value = "questionId", required = false) Long questionId,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "chapter", required = false) String chapter,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "history", required = false) String historyJson
    ) {
        long startedAt = System.nanoTime();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "图片或视频不能为空");
        }
        String contentType = detectMediaContentType(file);
        boolean image = contentType.startsWith("image/");
        boolean video = contentType.startsWith("video/");
        if (!image && !video) {
            throw new BusinessException(400, "仅支持图片或视频文件");
        }
        if (image && file.getSize() > 8 * 1024 * 1024) {
            throw new BusinessException(400, "图片不能超过 8MB，请压缩后重试");
        }
        if (video && file.getSize() > 30 * 1024 * 1024) {
            throw new BusinessException(400, "视频不能超过 30MB，请压缩后重试");
        }

        try {
            AiChatRequest request = new AiChatRequest();
            request.setConversationId(conversationId);
            prepareRequest(request);
            request.setMessage(StringUtils.hasText(message)
                    ? message.trim()
                    : "请分析这个图片或视频，并结合学习场景给出清晰讲解。");
            request.setContextTitle(contextTitle);
            request.setContextMeta(contextMeta);
            request.setContextExcerpt(contextExcerpt);
            request.setQuestionType(questionType);
            request.setQuestionId(questionId);
            request.setCourseId(courseId);
            request.setChapter(chapter);
            request.setHistory(parseHistory(historyJson));
            aiConversationService.restoreHistory(request);
            String dataUrl = "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(file.getBytes());
            if (image) {
                request.setImageData(dataUrl);
            } else {
                request.setVideoData(dataUrl);
            }

            AiDocumentService.RetrievedDocumentContext retrievedContext = enrichWithRetrievedDocument(request);
            AiChatVO response = kimiAiService.chat(request);
            if (retrievedContext != null) {
                response.setSources(retrievedContext.sources());
                response.setHasEvidence(retrievedContext.chunkCount() > 0);
                response.setRetrievalMode(retrievedContext.retrievalMode());
                response.setRetrievedChunkCount(retrievedContext.chunkCount());
            }
            finalizeResponse(request, response, startedAt);
            recordResponseSafely(request, response);
            return Result.success(response);
        } catch (IOException exception) {
            throw new BusinessException(400, "读取图片或视频失败，请重试");
        }
    }

    private void prepareRequest(AiChatRequest request) {
        if (request == null) return;
        request.setRequestId(UUID.randomUUID().toString());
        request.setConversationId(normalizeConversationId(request.getConversationId()));
    }

    /**
     * 客户端只需为每个对话窗口提供稳定 ID；缺失或格式非法时由服务端补一个新会话。
     * 侧边栏对旧数据合成的 legacy-row-* key 不是真实会话 ID，若被误传回来必须丢弃，
     * 否则会把这个合成 key 当作真实 conversationId 写库，造成历史脏数据。
     */
    private String normalizeConversationId(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            String trimmed = conversationId.trim();
            if (trimmed.length() <= 64
                    && trimmed.matches("[A-Za-z0-9_-]+")
                    && !trimmed.startsWith("legacy-row-")) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String detectMediaContentType(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (contentType.startsWith("image/") || contentType.startsWith("video/")) return contentType;
        String filename = safeFilename(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".webp")) return "image/webp";
        if (filename.endsWith(".gif")) return "image/gif";
        if (filename.endsWith(".mp4")) return "video/mp4";
        if (filename.endsWith(".mpeg") || filename.endsWith(".mpg")) return "video/mpeg";
        if (filename.endsWith(".webm")) return "video/webm";
        if (filename.endsWith(".mov")) return "video/quicktime";
        if (filename.endsWith(".avi")) return "video/x-msvideo";
        if (filename.endsWith(".flv")) return "video/x-flv";
        if (filename.endsWith(".wmv")) return "video/x-ms-wmv";
        if (filename.endsWith(".3gp") || filename.endsWith(".3gpp")) return "video/3gpp";
        return contentType;
    }

    private void recordResponseSafely(AiChatRequest request, AiChatVO response) {
        try {
            aiConversationService.recordSuccess(request, response);
        } catch (Exception exception) {
            // 观测表不可用时不能让用户已经获得的 AI 回答变成 500。
            log.warn("保存 AI 回答追踪记录失败，已保留回答本身", exception);
        }
    }

    private void finalizeResponse(AiChatRequest request, AiChatVO response, long startedAt) {
        if (response == null) return;
        response.setRequestId(request == null ? null : request.getRequestId());
        response.setConversationId(request == null ? null : request.getConversationId());
        response.setLatencyMs(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
        // KimiAiService 可能在“可选联网消歧”不可用时降级为 DIRECT，
        // 此处应保留实际执行路线，不能重新计算后又标成“联网核验”。
        String route = StringUtils.hasText(response.getRoute())
                ? response.getRoute()
                : kimiAiService.routeName(request);
        response.setRoute(route);
        if (!StringUtils.hasText(response.getAnswerMode())) {
            response.setAnswerMode(answerMode(request, route));
        }
        if (response.getHasEvidence() == null) {
            response.setHasEvidence(response.getSources() != null && !response.getSources().isEmpty());
        }
        response.setConfidence(confidence(route, Boolean.TRUE.equals(response.getHasEvidence())));
    }

    private String answerMode(AiChatRequest request, String route) {
        String task = request == null ? null : request.getAgentTask();
        if (StringUtils.hasText(task) && !"TUTOR".equalsIgnoreCase(task)) return "TEACHING_AGENT";
        if ("RAG".equals(route)) return "COURSE_RAG";
        if ("RAG_AND_WEB".equals(route)) return "RAG_AND_WEB";
        if ("WEB_SEARCH".equals(route)) return "WEB_SEARCH";
        return "DIRECT";
    }

    private String confidence(String route, boolean hasEvidence) {
        if ("WEB_SEARCH".equals(route) || "RAG_AND_WEB".equals(route)) return "UNVERIFIED";
        if (hasEvidence) return "HIGH";
        if ("RAG".equals(route)) return "LOW";
        return "MEDIUM";
    }

    private List<AiChatRequest.HistoryMessage> parseHistory(String historyJson) {
        if (!StringUtils.hasText(historyJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson,
                    new TypeReference<List<AiChatRequest.HistoryMessage>>() {});
        } catch (Exception exception) {
            log.warn("解析 AI 文档分析历史消息失败，将按新对话处理");
            return new ArrayList<>();
        }
    }

    private AiDocumentService.RetrievedDocumentContext enrichWithRetrievedDocument(AiChatRequest request) {
        // 检索上下文只由服务端生成，忽略客户端直接提交的同名字段。
        request.setRetrievedDocumentContext(null);
        String retrievalQuery = buildRetrievalQuery(request);
        if (request.getDocumentId() != null) {
            AiDocumentService.RetrievedDocumentContext context = aiDocumentService.retrieveForCurrentUser(
                    request.getDocumentId(), retrievalQuery);
            request.setDocumentName(context.documentName());
            request.setRetrievedDocumentContext(context.content());
            request.setDocumentText(null);
            return context;
        }
        // 课程页不代表每个问题都是课程问题：生活咨询、平台使用和简单计算应保留通用助手能力。
        if (request.getCourseId() == null || !kimiAiService.shouldUseCourseKnowledge(request)) return null;
        AiDocumentService.RetrievedDocumentContext context = aiDocumentService.retrieveForCourse(
                request.getCourseId(), request.getChapter(), retrievalQuery,
                isBroadCourseRequest(request.getMessage()));
        request.setDocumentName(context.documentName());
        request.setRetrievedDocumentContext(context.content());
        request.setDocumentText(null);
        return context;
    }

    /**
     * 题目页常把真正题干放在 contextExcerpt，而 message 只有“解释这道题”；
     * 连续追问也可能只有“为什么”。检索时把受信任的页面上下文和最近一条用户问题合并，
     * 仅用于关键词召回，不把客户端内容直接当作知识库内容或系统指令。
     */
    private String buildRetrievalQuery(AiChatRequest request) {
        StringBuilder query = new StringBuilder();
        appendRetrievalPart(query, request.getMessage(), 3000);
        appendRetrievalPart(query, request.getContextTitle(), 300);
        appendRetrievalPart(query, request.getContextMeta(), 300);
        appendRetrievalPart(query, request.getContextExcerpt(), 5000);

        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.length() <= 80 && request.getHistory() != null) {
            for (int index = request.getHistory().size() - 1; index >= 0; index--) {
                AiChatRequest.HistoryMessage historyMessage = request.getHistory().get(index);
                if (historyMessage != null && "user".equals(historyMessage.getRole())
                        && StringUtils.hasText(historyMessage.getContent())) {
                    appendRetrievalPart(query, historyMessage.getContent(), 2200);
                    break;
                }
            }
        }
        String result = query.toString().trim();
        return result.length() <= 8000 ? result : result.substring(0, 8000);
    }

    private void appendRetrievalPart(StringBuilder query, String value, int maxLength) {
        if (!StringUtils.hasText(value)) return;
        String normalized = value.trim();
        if (normalized.length() > maxLength) normalized = normalized.substring(0, maxLength);
        if (query.length() > 0) query.append('\n');
        query.append(normalized);
    }

    /** 泛化课程问题（如“总结本课程”）在关键词不足时使用课程概览片段。 */
    private boolean isBroadCourseRequest(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("(?s).*(总结|概括|重点|知识体系|课程内容|课程介绍|学习目标|整体|概览|梳理|复习提纲|本课程|本章重点).*");
    }

    private String normalizeAgentTask(String task) {
        if (!StringUtils.hasText(task)) return "TUTOR";
        String normalized = task.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "TUTOR", "LESSON_PLAN", "QUESTION_GENERATION", "LEARNING_ANALYSIS", "KNOWLEDGE_MAP" -> normalized;
            default -> "TUTOR";
        };
    }

    private String safeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "上传文档";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
