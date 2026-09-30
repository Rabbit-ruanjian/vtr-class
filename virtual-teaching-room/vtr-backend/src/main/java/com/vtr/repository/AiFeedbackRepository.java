package com.vtr.repository;

import com.vtr.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    Optional<AiFeedback> findByConversationIdAndUserId(Long conversationId, Long userId);

    long deleteByUserId(Long userId);

    /** 删除某个账号在指定问答行上的反馈，配合会话删除一并清理。 */
    long deleteByUserIdAndConversationIdIn(Long userId, Collection<Long> conversationIds);
}
