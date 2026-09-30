package com.vtr.entity;

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

/** 课程 AI 评测样本：用于比较检索路由、证据和回答要点是否稳定。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_evaluation_case", indexes = {
        @Index(name = "idx_ai_eval_course_enabled", columnList = "course_id, enabled"),
        @Index(name = "idx_ai_eval_creator", columnList = "created_by")
})
public class AiEvaluationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "chapter", length = 100)
    private String chapter;

    /** 逗号、顿号、分号或换行分隔的必须出现的答案要点。 */
    @Column(name = "required_keywords", columnDefinition = "TEXT")
    private String requiredKeywords;

    /** 逗号、顿号、分号或换行分隔的禁止出现的错误词。 */
    @Column(name = "forbidden_keywords", columnDefinition = "TEXT")
    private String forbiddenKeywords;

    @Column(name = "expected_route", length = 40)
    private String expectedRoute;

    @Column(name = "expected_evidence", nullable = false)
    private Boolean expectedEvidence = true;

    @Column(nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
