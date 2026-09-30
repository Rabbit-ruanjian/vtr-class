package com.vtr.service;

import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AiChatRequest;
import com.vtr.dto.AiEvaluationCaseUpsertDTO;
import com.vtr.entity.AiEvaluationCase;
import com.vtr.repository.AiEvaluationCaseRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.vo.AiChatVO;
import com.vtr.vo.AiEvaluationCaseVO;
import com.vtr.vo.AiEvaluationResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 课程 AI 评测服务。评测样本由教师维护，运行时复用真实的课程检索和回答链路。
 */
@Service
@RequiredArgsConstructor
public class AiEvaluationService {
    private final AiEvaluationCaseRepository evaluationCaseRepository;
    private final AiDocumentService aiDocumentService;
    private final KimiAiService kimiAiService;

    @Transactional(readOnly = true)
    public List<AiEvaluationCaseVO> list(Long courseId) {
        aiDocumentService.requireCourseAccess(courseId);
        return evaluationCaseRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public AiEvaluationCaseVO create(Long courseId, AiEvaluationCaseUpsertDTO dto) {
        Long userId = aiDocumentService.requireCourseEditor(courseId);
        AiEvaluationCase item = new AiEvaluationCase();
        item.setCourseId(courseId);
        item.setCreatedBy(userId);
        copy(dto, item);
        return toVO(evaluationCaseRepository.save(item));
    }

    public List<AiEvaluationResultVO> run(Long courseId) {
        aiDocumentService.requireCourseEditor(courseId);
        List<AiEvaluationCase> cases = evaluationCaseRepository
                .findByCourseIdAndEnabledTrueOrderByCreatedAtAsc(courseId);
        List<AiEvaluationResultVO> results = new ArrayList<>(cases.size());
        for (AiEvaluationCase item : cases) {
            results.add(runOne(item));
        }
        return results;
    }

    private AiEvaluationResultVO runOne(AiEvaluationCase item) {
        long startedAt = System.nanoTime();
        AiChatRequest request = new AiChatRequest();
        request.setMessage(item.getQuestion());
        request.setCourseId(item.getCourseId());
        request.setChapter(item.getChapter());

        AiDocumentService.RetrievedDocumentContext context = aiDocumentService.retrieveForCourse(
                item.getCourseId(), item.getChapter(), item.getQuestion());
        request.setRetrievedDocumentContext(context.content());
        request.setDocumentName(context.documentName());

        AiChatVO answer;
        try {
            answer = kimiAiService.chat(request);
        } catch (Exception exception) {
            throw new BusinessException(502, "评测样本“" + item.getTitle() + "”执行失败：" + exception.getMessage());
        }

        String missing = missingKeywords(answer == null ? null : answer.getReply(), item.getRequiredKeywords());
        String forbidden = foundKeywords(answer == null ? null : answer.getReply(), item.getForbiddenKeywords());
        String actualRoute = answer == null ? null : answer.getRoute();
        boolean routePassed = !StringUtils.hasText(item.getExpectedRoute())
                || item.getExpectedRoute().trim().equalsIgnoreCase(actualRoute == null ? "" : actualRoute);
        boolean evidencePassed = item.getExpectedEvidence() == null
                || item.getExpectedEvidence().equals(answer != null && Boolean.TRUE.equals(answer.getHasEvidence()));
        boolean passed = routePassed && evidencePassed && !StringUtils.hasText(missing)
                && !StringUtils.hasText(forbidden) && answer != null && StringUtils.hasText(answer.getReply());

        AiEvaluationResultVO result = new AiEvaluationResultVO();
        result.setCaseId(item.getId());
        result.setTitle(item.getTitle());
        result.setQuestion(item.getQuestion());
        result.setExpectedRoute(item.getExpectedRoute());
        result.setActualRoute(actualRoute);
        result.setRoutePassed(routePassed);
        result.setEvidencePassed(evidencePassed);
        result.setMissingKeywords(missing);
        result.setForbiddenKeywordsFound(forbidden);
        result.setPassed(passed);
        result.setAnswerExcerpt(trim(answer == null ? null : answer.getReply(), 500));
        result.setLatencyMs(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
        return result;
    }

    private void copy(AiEvaluationCaseUpsertDTO dto, AiEvaluationCase item) {
        if (dto == null) throw new BusinessException(400, "评测样本不能为空");
        item.setTitle(dto.getTitle().trim());
        item.setQuestion(dto.getQuestion().trim());
        item.setChapter(trimToNull(dto.getChapter()));
        item.setRequiredKeywords(trimToNull(dto.getRequiredKeywords()));
        item.setForbiddenKeywords(trimToNull(dto.getForbiddenKeywords()));
        item.setExpectedRoute(normalizeRoute(dto.getExpectedRoute()));
        item.setExpectedEvidence(dto.getExpectedEvidence() == null ? true : dto.getExpectedEvidence());
        item.setEnabled(dto.getEnabled() == null ? true : dto.getEnabled());
    }

    private String normalizeRoute(String route) {
        if (!StringUtils.hasText(route)) return null;
        String normalized = route.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DIRECT", "RAG", "WEB_SEARCH", "RAG_AND_WEB").contains(normalized)) {
            throw new BusinessException(400, "期望路由必须是 DIRECT、RAG、WEB_SEARCH 或 RAG_AND_WEB");
        }
        return normalized;
    }

    private String missingKeywords(String answer, String keywords) {
        if (!StringUtils.hasText(keywords)) return "";
        List<String> missing = splitKeywords(keywords).stream()
                .filter(keyword -> !containsNormalized(answer, keyword))
                .toList();
        return String.join("、", missing);
    }

    private String foundKeywords(String answer, String keywords) {
        if (!StringUtils.hasText(keywords)) return "";
        List<String> found = splitKeywords(keywords).stream()
                .filter(keyword -> containsNormalized(answer, keyword))
                .toList();
        return String.join("、", found);
    }

    private List<String> splitKeywords(String value) {
        return Arrays.stream(value.split("[,，、;；\\n]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean containsNormalized(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) return false;
        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);
        if (normalizedText.contains(normalizedKeyword)) return true;
        // 评测允许模型用中文回答英文课程术语；要求“保留英文术语”时，
        // 不应把合法的中英双语表达误判为缺少答案要点。
        return switch (normalizedKeyword) {
            case "protection" -> normalizedText.contains("保护")
                    || normalizedText.contains("隔离")
                    || normalizedText.contains("权限");
            case "memory" -> normalizedText.contains("内存")
                    || normalizedText.contains("存储器");
            case "kernel" -> normalizedText.contains("内核")
                    || normalizedText.contains("核心态")
                    || normalizedText.contains("内核态");
            case "device" -> normalizedText.contains("设备")
                    || normalizedText.contains("驱动");
            case "virtualmemory" -> normalizedText.contains("虚拟内存")
                    || normalizedText.contains("虚拟存储")
                    || normalizedText.contains("内存虚拟化")
                    || normalizedText.contains("地址空间虚拟化")
                    || normalizedText.contains("虚拟地址空间")
                    // page fault 的解释经常直接从虚拟地址、页表和分页机制展开；
                    // 这些是 virtual memory 在本课程语境下的等价证据，不应因中文措辞不同误判。
                    || normalizedText.contains("虚拟地址")
                    || normalizedText.contains("页表")
                    || normalizedText.contains("分页");
            case "concurrency" -> normalizedText.contains("并发")
                    || normalizedText.contains("并行")
                    || normalizedText.contains("竞争");
            case "filesystem" -> normalizedText.contains("文件系统")
                    || normalizedText.contains("文件管理");
            default -> false;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{P}\\p{S}]+", "");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trim(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private AiEvaluationCaseVO toVO(AiEvaluationCase item) {
        AiEvaluationCaseVO vo = new AiEvaluationCaseVO();
        vo.setId(item.getId());
        vo.setCourseId(item.getCourseId());
        vo.setTitle(item.getTitle());
        vo.setQuestion(item.getQuestion());
        vo.setChapter(item.getChapter());
        vo.setRequiredKeywords(item.getRequiredKeywords());
        vo.setForbiddenKeywords(item.getForbiddenKeywords());
        vo.setExpectedRoute(item.getExpectedRoute());
        vo.setExpectedEvidence(item.getExpectedEvidence());
        vo.setEnabled(item.getEnabled());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }
}
