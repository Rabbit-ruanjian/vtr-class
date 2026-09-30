package com.vtr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtr.common.exception.BusinessException;
import com.vtr.dto.AiChatRequest;
import com.vtr.dto.AiFeedbackRequest;
import com.vtr.entity.AiConversation;
import com.vtr.entity.AiFeedback;
import com.vtr.repository.AiConversationRepository;
import com.vtr.repository.AiFeedbackRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.vo.AiChatVO;
import com.vtr.vo.AiConversationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** AI 回答审计与反馈服务。记录失败不会影响用户已经拿到的回答。 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

    /** 与 AiChatRequest 的校验规则一致，避免持久化历史把模型上下文无限撑大。 */
    private static final int HISTORY_LIMIT = 10;
    private static final int HISTORY_CONTENT_LIMIT = 8_000;
    /** 旧数据没有会话 ID 时，为侧边栏合成的稳定 key 前缀，删除时据此按行处理。 */
    private static final String LEGACY_ROW_PREFIX = "legacy-row-";

    private final AiConversationRepository conversationRepository;
    private final AiFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    /** 返回当前登录账号最近的问答，供历史面板和服务端记忆使用。 */
    @Transactional(readOnly = true)
    public List<AiConversationSummary> recentForCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(401, "请先登录后查看 AI 历史记录");
        List<AiConversation> rows = conversationRepository.findTop200ByUserIdOrderByCreatedAtDescIdDesc(userId);
        return groupByConversation(rows);
    }

    /**
     * 把逐行问答按会话聚合成侧边栏记录：同一个对话窗口只出现一条，
     * 只有用户点击“新对话”生成新的 conversationId 时才会新增一条。
     * 旧数据没有 conversationId 时退回按行展示，保证历史仍可访问。
     */
    private List<AiConversationSummary> groupByConversation(List<AiConversation> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        // rows 为时间倒序；用有序 Map 保持“最近活跃的会话在前”。
        Map<String, List<AiConversation>> grouped = new LinkedHashMap<>();
        for (AiConversation row : rows) {
            if (row == null) continue;
            // 只有真正合法的 conversationId 才用于聚合；合成 legacy key 绝不写回数据库，
            // 因此这里遇到形如 legacy-row-* 的历史脏数据时按行独立展示，避免二次污染。
            String realId = row.getConversationId();
            String key = (StringUtils.hasText(realId) && !realId.startsWith(LEGACY_ROW_PREFIX))
                    ? realId
                    : LEGACY_ROW_PREFIX + row.getId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<AiConversationSummary> summaries = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<AiConversation>> entry : grouped.entrySet()) {
            List<AiConversation> desc = entry.getValue();
            // desc 为倒序：正序副本用于恢复窗口，倒序首元素是最近一轮。
            List<AiConversation> chronological = new ArrayList<>(desc);
            java.util.Collections.reverse(chronological);
            AiConversation latest = desc.get(0);
            AiConversation earliest = chronological.get(0);

            String groupKey = entry.getKey();
            boolean legacy = groupKey.startsWith(LEGACY_ROW_PREFIX);

            AiConversationSummary summary = new AiConversationSummary();
            summary.setKey(groupKey);
            // 旧数据没有真实会话 ID，这里保持为 null，前端不会把它当作“继续同一对话”的上下文。
            summary.setConversationId(legacy ? null : groupKey);
            summary.setId(latest.getId());
            summary.setTitle(firstNonBlank(earliest.getQuestion(), latest.getQuestion()));
            summary.setQuestion(earliest.getQuestion());
            summary.setAnswer(latest.getAnswer());
            summary.setDocumentId(latestDocumentId(desc));
            summary.setCreatedAt(latest.getCreatedAt());
            summary.setTurnCount(chronological.size());
            summary.setTurns(chronological);
            summaries.add(summary);
        }
        return summaries;
    }

    private Long latestDocumentId(List<AiConversation> descRows) {
        for (AiConversation row : descRows) {
            if (row.getDocumentId() != null) return row.getDocumentId();
        }
        return null;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) return primary;
        return StringUtils.hasText(fallback) ? fallback : "";
    }

    /** 把数据库中的最近问答恢复为模型上下文，确保重启或换设备后仍能连续对话。 */
    @Transactional(readOnly = true)
    public void restoreHistory(AiChatRequest request) {
        if (request == null) return;
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return;
        List<AiChatRequest.HistoryMessage> current = sanitizeHistory(request.getHistory());
        if (current.size() > HISTORY_LIMIT) {
            current = new ArrayList<>(current.subList(current.size() - HISTORY_LIMIT, current.size()));
        }
        List<AiConversation> saved;
        try {
            // 只恢复“当前对话窗口”的问答；不同侧边栏对话之间的上下文相互隔离。
            if (StringUtils.hasText(request.getConversationId())) {
                saved = conversationRepository
                        .findByUserIdAndConversationIdOrderByCreatedAtAscIdAsc(userId, request.getConversationId());
            } else {
                saved = List.of();
            }
        } catch (Exception ignored) {
            // 历史表尚未迁移时仍允许本次 AI 请求正常回答。
            request.setHistory(current);
            return;
        }
        if (saved == null) saved = List.of();
        List<AiChatRequest.HistoryMessage> restored = new ArrayList<>();
        // saved 已按时间正序返回，直接顺序展开为 user/assistant 轮次。
        for (AiConversation item : saved) {
            if (StringUtils.hasText(item.getQuestion())) restored.add(history("user", item.getQuestion()));
            if (StringUtils.hasText(item.getAnswer())) restored.add(history("assistant", item.getAnswer()));
        }

        // 前端通常也会带回最近历史；只移除“数据库尾部 = 客户端开头”的重叠部分，
        // 既避免同一轮被送给模型两次，也不会错误删除用户在不同轮次的相同文本。
        int overlap = sharedSuffixPrefixSize(restored, current);
        restored.addAll(current.subList(overlap, current.size()));
        int from = Math.max(0, restored.size() - HISTORY_LIMIT);
        request.setHistory(new ArrayList<>(restored.subList(from, restored.size())));
    }

    /** 删除的范围严格限定为当前认证账号，不影响任何其他账号的对话记忆。 */
    @Transactional
    public void clearForCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(401, "请先登录后再清除 AI 历史记录");
        feedbackRepository.deleteByUserId(userId);
        conversationRepository.deleteByUserId(userId);
    }

    /**
     * 删除当前账号侧边栏里的某一条“对话”。
     * - 新数据：按 conversationId 删除整段对话窗口的全部轮次；
     * - 旧数据（无 conversationId）：侧边栏 key 形如 legacy-row-{id}，按行删除。
     * 删除范围严格限定为当前账号，不影响其他账号，也不影响课程资料。
     */
    @Transactional
    public void deleteForCurrentUser(String conversationKey) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(401, "请先登录后再删除对话");
        if (!StringUtils.hasText(conversationKey)) {
            throw new BusinessException(400, "缺少要删除的对话标识");
        }
        String key = conversationKey.trim();

        List<AiConversation> rows;
        if (key.startsWith(LEGACY_ROW_PREFIX)) {
            Long rowId = parseLegacyRowId(key);
            if (rowId == null) throw new BusinessException(400, "对话标识不合法");
            rows = conversationRepository.findByUserIdAndConversationIdOrderByCreatedAtAscIdAsc(userId, key);
            // 旧数据没有 conversationId，走单行删除。
            deleteFeedbackForRows(userId, List.of(rowId));
            long removed = conversationRepository.deleteByUserIdAndId(userId, rowId);
            if (removed == 0) throw new BusinessException(404, "找不到要删除的对话，请刷新后重试");
            return;
        }

        rows = conversationRepository.findByUserIdAndConversationId(userId, key);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(404, "找不到要删除的对话，请刷新后重试");
        }
        List<Long> rowIds = new ArrayList<>(rows.size());
        for (AiConversation row : rows) rowIds.add(row.getId());
        deleteFeedbackForRows(userId, rowIds);
        conversationRepository.deleteByUserIdAndConversationId(userId, key);
    }

    private void deleteFeedbackForRows(Long userId, List<Long> rowIds) {
        if (rowIds == null || rowIds.isEmpty()) return;
        try {
            feedbackRepository.deleteByUserIdAndConversationIdIn(userId, rowIds);
        } catch (Exception ignored) {
            // 反馈表缺失或无匹配时不影响对话本身的删除。
        }
    }

    private Long parseLegacyRowId(String key) {
        try {
            return Long.valueOf(key.substring(LEGACY_ROW_PREFIX.length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<AiChatRequest.HistoryMessage> sanitizeHistory(
            List<AiChatRequest.HistoryMessage> history) {
        List<AiChatRequest.HistoryMessage> sanitized = new ArrayList<>();
        if (history == null) return sanitized;
        for (AiChatRequest.HistoryMessage item : history) {
            if (item == null || !StringUtils.hasText(item.getContent())) continue;
            String role = item.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            sanitized.add(history(role, item.getContent()));
        }
        return sanitized;
    }

    private int sharedSuffixPrefixSize(List<AiChatRequest.HistoryMessage> saved,
                                       List<AiChatRequest.HistoryMessage> current) {
        for (int size = Math.min(saved.size(), current.size()); size > 0; size--) {
            boolean matched = true;
            for (int index = 0; index < size; index++) {
                AiChatRequest.HistoryMessage left = saved.get(saved.size() - size + index);
                AiChatRequest.HistoryMessage right = current.get(index);
                if (!Objects.equals(left.getRole(), right.getRole())
                        || !Objects.equals(left.getContent(), right.getContent())) {
                    matched = false;
                    break;
                }
            }
            if (matched) return size;
        }
        return 0;
    }

    private AiChatRequest.HistoryMessage history(String role, String content) {
        AiChatRequest.HistoryMessage item = new AiChatRequest.HistoryMessage();
        item.setRole(role);
        item.setContent(trim(content, HISTORY_CONTENT_LIMIT));
        return item;
    }

    private String resolveConversationId(AiChatRequest request, AiChatVO response) {
        if (request != null && StringUtils.hasText(request.getConversationId())) {
            return request.getConversationId();
        }
        if (response != null && StringUtils.hasText(response.getConversationId())) {
            return response.getConversationId();
        }
        // 极端情况下仍给一条独立会话 ID，避免与其他窗口错误聚合。
        return response != null && StringUtils.hasText(response.getRequestId())
                ? response.getRequestId()
                : null;
    }

    @Transactional
    public void recordSuccess(AiChatRequest request, AiChatVO response) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null || response == null || !StringUtils.hasText(response.getRequestId())) {
            return;
        }
        AiConversation item = new AiConversation();
        item.setRequestId(response.getRequestId());
        item.setConversationId(resolveConversationId(request, response));
        item.setUserId(userId);
        item.setCourseId(request == null ? null : request.getCourseId());
        item.setDocumentId(response.getDocumentId() != null
                ? response.getDocumentId() : request == null ? null : request.getDocumentId());
        item.setQuestion(request == null ? "" : trim(request.getMessage(), 8000));
        item.setAnswer(trim(response.getReply(), 100000));
        item.setModel(response.getModel());
        item.setRoute(response.getRoute());
        item.setAnswerMode(response.getAnswerMode());
        item.setConfidence(response.getConfidence());
        item.setHasEvidence(Boolean.TRUE.equals(response.getHasEvidence()));
        item.setLatencyMs(response.getLatencyMs());
        item.setRetrievalMode(response.getRetrievalMode());
        item.setSourcesJson(writeSources(response.getSources()));
        conversationRepository.save(item);
    }

    @Transactional
    public void saveFeedback(AiFeedbackRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(401, "请先登录后再提交反馈");
        String requestId = request == null || request.getRequestId() == null
                ? "" : request.getRequestId().trim();
        AiConversation conversation = conversationRepository.findByRequestIdAndUserId(requestId, userId)
                .orElseThrow(() -> new BusinessException(404, "找不到这条 AI 回答，请刷新后重试"));

        AiFeedback feedback = feedbackRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                .orElseGet(AiFeedback::new);
        feedback.setConversationId(conversation.getId());
        feedback.setUserId(userId);
        feedback.setHelpful(request.getHelpful());
        feedback.setFeedbackType(normalizeType(request.getFeedbackType(), request.getHelpful()));
        feedback.setComment(trimToNull(request.getComment(), 1000));
        feedbackRepository.save(feedback);
    }

    private String normalizeType(String value, Boolean helpful) {
        if (StringUtils.hasText(value)) return trim(value, 30).toUpperCase();
        return Boolean.TRUE.equals(helpful) ? "HELPFUL" : "NOT_HELPFUL";
    }

    private String writeSources(List<?> sources) {
        try { return objectMapper.writeValueAsString(sources == null ? List.of() : sources); }
        catch (Exception ignored) { return "[]"; }
    }

    private String trim(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String trimToNull(String value, int maxLength) {
        String text = trim(value, maxLength);
        return StringUtils.hasText(text) ? text : null;
    }
}
