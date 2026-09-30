package com.vtr.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/** AI 教研 Agent 生成的可审核产物。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_agent_artifact", indexes = {
        @Index(name = "idx_ai_artifact_course", columnList = "course_id, status"),
        @Index(name = "idx_ai_artifact_creator", columnList = "created_by")
})
public class AiAgentArtifact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob @Column(name = "sources_json")
    private String sourcesJson;

    @Column(nullable = false, length = 30)
    private String status = "PENDING_REVIEW";

    @Column(name = "review_remark", length = 1000)
    private String reviewRemark;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
