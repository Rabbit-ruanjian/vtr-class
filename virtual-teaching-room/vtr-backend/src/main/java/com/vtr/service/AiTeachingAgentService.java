package com.vtr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AiChatRequest;
import com.vtr.entity.AiAgentArtifact;
import com.vtr.repository.AiAgentArtifactRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.vo.AiAgentArtifactVO;
import com.vtr.vo.AiChatVO;
import com.vtr.vo.AiSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 课程级 Agent 编排器：检索本地知识库，生成可审核的教学产物。 */
@Service
@RequiredArgsConstructor
public class AiTeachingAgentService {
    private final AiDocumentService aiDocumentService;
    private final KimiAiService kimiAiService;
    private final AiAgentOutputValidator outputValidator;
    private final AiAgentArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiAgentArtifactVO execute(AiChatRequest request) {
        long startedAt = System.nanoTime();
        if (request == null || request.getCourseId() == null) throw new BusinessException(400, "课程不能为空");
        if (!StringUtils.hasText(request.getMessage())) throw new BusinessException(400, "Agent任务不能为空");
        String task = normalizeTask(request.getAgentTask());
        // 教案、题目和学情分析属于课程教研产物，必须由课程教师团队发起。
        aiDocumentService.requireCourseEditor(request.getCourseId());

        request.setAgentTask(task);
        request.setRetrievedDocumentContext(null);
        AiDocumentService.RetrievedDocumentContext context = aiDocumentService.retrieveForCourse(
                request.getCourseId(), request.getChapter(), request.getMessage(), true);
        request.setDocumentName(context.documentName());
        request.setRetrievedDocumentContext(context.content());
        AiChatVO answer = kimiAiService.chat(request);
        // Agent 入口不经过 AiAssistantController，必须在这里把检索依据显式挂到产物上。
        answer.setSources(context.sources());
        answer.setHasEvidence(context.chunkCount() > 0);
        answer.setRetrievalMode(context.retrievalMode());
        answer.setRetrievedChunkCount(context.chunkCount());
        answer.setLatencyMs(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));

        AiAgentArtifact artifact = new AiAgentArtifact();
        artifact.setCourseId(request.getCourseId());
        artifact.setCreatedBy(SecurityUtils.getCurrentUserId());
        artifact.setTaskType(task);
        artifact.setTitle(titleOf(task, request));
        artifact.setContent(answer.getReply());
        artifact.setSourcesJson(writeSources(answer.getSources()));
        LocalDateTime now = LocalDateTime.now();
        // 教学大纲无需管理员审核，AI 生成后直接通过；其余产物保持审核流程。
        String status = switch (task) {
            case "TUTOR" -> "USED";
            case "TEACHING_OUTLINE" -> "APPROVED";
            default -> "PENDING_REVIEW";
        };
        artifact.setStatus(status);
        artifact.setReviewRemark(outputValidator.warning(task, answer.getReply(),
                Boolean.TRUE.equals(answer.getHasEvidence())));
        if ("APPROVED".equals(status)) {
            artifact.setReviewedBy(SecurityUtils.getCurrentUserId());
            artifact.setReviewedAt(now);
        }
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        return toVO(artifactRepository.save(artifact), answer.getSources(), answer);
    }

    @Transactional(readOnly = true)
    public List<AiAgentArtifactVO> list(Long courseId) {
        aiDocumentService.requireCourseAccess(courseId);
        boolean editor = aiDocumentService.isCourseEditor(courseId);
        return artifactRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                // 未审核的教案、题目和学情分析属于教师工作草稿，学生只能看到教师审核通过的产物。
                .filter(item -> editor || "APPROVED".equals(item.getStatus()))
                .map(item -> toVO(item, readSources(item.getSourcesJson())))
                .toList();
    }

    @Transactional
    public AiAgentArtifactVO review(Long id, String action, String remark) {
        AiAgentArtifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "AI产物不存在"));
        aiDocumentService.requireCourseEditor(artifact.getCourseId());
        if (!"PENDING_REVIEW".equals(artifact.getStatus())) {
            throw new BusinessException(409, "该 AI 产物已经审核过，不能重复审核");
        }
        if (!"APPROVE".equalsIgnoreCase(action) && !"REJECT".equalsIgnoreCase(action)) {
            throw new BusinessException(400, "审核操作无效");
        }
        artifact.setStatus("APPROVE".equalsIgnoreCase(action) ? "APPROVED" : "REJECTED");
        artifact.setReviewRemark(remark == null ? null : remark.trim());
        artifact.setReviewedBy(SecurityUtils.getCurrentUserId());
        artifact.setReviewedAt(LocalDateTime.now());
        artifact.setUpdatedAt(LocalDateTime.now());
        return toVO(artifactRepository.save(artifact), readSources(artifact.getSourcesJson()));
    }

    private String normalizeTask(String task) {
        if (!StringUtils.hasText(task)) return "TUTOR";
        String normalized = task.trim().toUpperCase();
        return switch (normalized) {
            case "TUTOR", "LESSON_PLAN", "QUESTION_GENERATION", "LEARNING_ANALYSIS", "KNOWLEDGE_MAP", "TEACHING_OUTLINE" -> normalized;
            default -> throw new BusinessException(400, "不支持的 Agent 任务类型");
        };
    }

    private String titleOf(String task, AiChatRequest request) {
        String prefix = switch (task) {
            case "LESSON_PLAN" -> "AI备课方案";
            case "QUESTION_GENERATION" -> "AI题目草稿";
            case "LEARNING_ANALYSIS" -> "AI学情分析";
            case "KNOWLEDGE_MAP" -> "AI知识图谱";
            case "TEACHING_OUTLINE" -> "AI教学大纲草稿";
            default -> "AI学习辅导";
        };
        return prefix + (StringUtils.hasText(request.getChapter()) ? " · " + request.getChapter() : "");
    }

    private String writeSources(List<AiSourceVO> sources) {
        try { return objectMapper.writeValueAsString(sources == null ? List.of() : sources); }
        catch (Exception ignored) { return "[]"; }
    }

    private List<AiSourceVO> readSources(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<List<AiSourceVO>>() {});
        } catch (Exception ignored) { return List.of(); }
    }

    private AiAgentArtifactVO toVO(AiAgentArtifact item, List<AiSourceVO> sources) {
        AiAgentArtifactVO vo = toVO(item, sources, null);
        vo.setRoute("RAG");
        vo.setAnswerMode("TEACHING_AGENT");
        vo.setHasEvidence(sources != null && !sources.isEmpty());
        vo.setConfidence(vo.getHasEvidence() ? "HIGH" : "LOW");
        return vo;
    }

    private AiAgentArtifactVO toVO(AiAgentArtifact item, List<AiSourceVO> sources, AiChatVO answer) {
        AiAgentArtifactVO vo = new AiAgentArtifactVO();
        vo.setId(item.getId()); vo.setCourseId(item.getCourseId()); vo.setCreatedBy(item.getCreatedBy());
        vo.setTaskType(item.getTaskType()); vo.setTitle(item.getTitle()); vo.setContent(item.getContent());
        vo.setStatus(item.getStatus()); vo.setReviewRemark(item.getReviewRemark()); vo.setCreatedAt(item.getCreatedAt());
        vo.setSources(sources == null ? List.of() : sources);
        if (answer != null) {
            vo.setRequestId(answer.getRequestId());
            vo.setRoute(answer.getRoute());
            vo.setAnswerMode(answer.getAnswerMode());
            vo.setConfidence(answer.getConfidence());
            vo.setHasEvidence(answer.getHasEvidence());
            vo.setLatencyMs(answer.getLatencyMs());
            vo.setRetrievalMode(answer.getRetrievalMode());
            vo.setRetrievedChunkCount(answer.getRetrievedChunkCount());
            vo.setQualityStatus(answer.getQualityStatus());
        }
        return vo;
    }
}
