package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** AI 助手一次完整回答的可追踪记录。只保存必要的问答审计信息，不保存 API Key。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_conversation", indexes = {
        @Index(name = "idx_ai_conversation_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_ai_conversation_user_conversation", columnList = "user_id, conversation_id, created_at"),
        @Index(name = "idx_ai_conversation_course_created", columnList = "course_id, created_at"),
        @Index(name = "uk_ai_conversation_request", columnList = "request_id", unique = true)
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    /** 同一个对话窗口的所有问答共享该 ID，新建对话时才会变化，用于侧边栏按会话聚合。 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "question", nullable = false, columnDefinition = "LONGTEXT")
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "route", length = 40)
    private String route;

    @Column(name = "answer_mode", length = 40)
    private String answerMode;

    @Column(name = "confidence", length = 20)
    private String confidence;

    @Column(name = "has_evidence", nullable = false)
    private Boolean hasEvidence = false;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "retrieval_mode", length = 40)
    private String retrievalMode;

    @Column(name = "sources_json", columnDefinition = "LONGTEXT")
    private String sourcesJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
