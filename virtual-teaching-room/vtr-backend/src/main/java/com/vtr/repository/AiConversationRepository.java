package com.vtr.repository;

import com.vtr.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {
    Optional<AiConversation> findByRequestIdAndUserId(String requestId, Long userId);

    List<AiConversation> findTop30ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 取更多行用于按会话聚合，聚合后侧边栏展示的会话数会明显少于行数。 */
    List<AiConversation> findTop200ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 只恢复当前对话窗口的问答，避免把其他会话的上下文混进模型。 */
    List<AiConversation> findByUserIdAndConversationIdOrderByCreatedAtAscIdAsc(Long userId, String conversationId);

    long deleteByUserId(Long userId);

    /** 删除当前账号某一整个对话窗口（同一 conversationId 的所有轮次）。 */
    long deleteByUserIdAndConversationId(Long userId, String conversationId);

    /** 删除旧数据中没有 conversationId 的单行问答。 */
    long deleteByUserIdAndId(Long userId, Long id);

    List<AiConversation> findByUserIdAndConversationId(Long userId, String conversationId);
}
