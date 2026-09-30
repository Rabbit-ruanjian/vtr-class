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
@Table(name = "research_task_contribution", uniqueConstraints =
        @UniqueConstraint(name = "uk_research_task_contributor", columnNames = {"task_id", "contributor_id"}))
public class ResearchTaskContribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_research_task_contribution_task"))
    private ResearchTask task;

    @Column(name = "contributor_id", nullable = false)
    private Long contributorId;

    @Column(nullable = false, length = 20)
    private String status = "CLAIMED";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", foreignKey = @ForeignKey(name = "fk_research_task_contribution_resource"))
    private Courseware resource;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt = LocalDateTime.now();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Lob
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;
}
