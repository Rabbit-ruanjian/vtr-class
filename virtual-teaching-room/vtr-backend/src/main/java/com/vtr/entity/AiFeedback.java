package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 用户对某次 AI 回答的反馈；同一用户可以修改自己的反馈，但不能反馈他人的回答。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_feedback", indexes = {
        @Index(name = "idx_ai_feedback_conversation", columnList = "conversation_id"),
        @Index(name = "uk_ai_feedback_conversation_user", columnList = "conversation_id, user_id", unique = true)
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "helpful", nullable = false)
    private Boolean helpful;

    @Column(name = "feedback_type", length = 30)
    private String feedbackType;

    @Column(name = "comment", length = 1000)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
