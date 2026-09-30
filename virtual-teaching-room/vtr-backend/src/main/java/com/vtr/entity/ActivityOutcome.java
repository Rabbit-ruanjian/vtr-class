package com.vtr.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "activity_outcome", indexes = {
        @Index(name = "idx_activity_outcome_activity", columnList = "activity_id"),
        @Index(name = "idx_activity_outcome_task", columnList = "task_id"),
        @Index(name = "idx_activity_outcome_status", columnList = "status")
})
public class ActivityOutcome {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false, foreignKey = @ForeignKey(name = "fk_activity_outcome_activity"))
    private TeachingActivity activity;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_activity_outcome_task"))
    private ResearchTask task;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", foreignKey = @ForeignKey(name = "fk_activity_outcome_resource"))
    private Courseware resource;
    @Column(nullable = false, length = 200) private String title;
    @Lob @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, length = 30) private String type;
    @Column(nullable = false, length = 20) private String status = "SUBMITTED";
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "review_comment", length = 1000) private String reviewComment;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
}
