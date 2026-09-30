package com.vtr.entity;

import com.vtr.common.BaseEntity;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_audit", indexes = {
        @Index(name = "idx_content", columnList = "content_type, content_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_reviewer", columnList = "reviewer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentAudit extends BaseEntity {  // 继承 BaseEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 30)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "content_title", length = 200)
    private String contentTitle;

    @Lob
    @Column(name = "content_preview", columnDefinition = "TEXT")
    private String contentPreview;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", length = 50)
    private String authorName;

    @Column(name = "school_id")
    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuditStatus status = AuditStatus.PENDING;

    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private String riskLevel = "LOW";

    @Lob
    @Column(name = "auto_check_result", columnDefinition = "TEXT")
    private String autoCheckResult;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Lob
    @Column(name = "review_remark", columnDefinition = "TEXT")
    private String reviewRemark;

    @Column(name = "submit_count")
    @Builder.Default
    private Integer submitCount = 1;

    public enum ContentType {
        SNIPPET("代码片段"),
        ASSIGNMENT("作业"),
        SUBMISSION("提交"),
        NOTICE("公告"),
        COMMENT("评论"),
        FORUM_POST("社区帖子"),
        FORUM_COMMENT("社区评论"),
        COURSEWARE("教学资源"),
        ACTIVITY("教研活动");

        private final String description;

        ContentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AuditStatus {
        PENDING("待审核"),
        PASS("通过"),
        // 兼容历史数据：旧版本使用 APPROVED/REJECTED 命名审核结果。
        APPROVED("已通过"),
        REJECT("拒绝"),
        REJECTED("已拒绝"),
        NEED_REVIEW("需要人工复核");

        private final String description;

        AuditStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 实现 BaseEntity 的抽象方法
    @Override
    public boolean isEnabled() {
        return this.status == AuditStatus.PASS || this.status == AuditStatus.APPROVED;
    }

    // 业务方法
    public void pass(Long reviewerId, String reviewerName, String remark) {
        this.status = AuditStatus.PASS;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewedAt = LocalDateTime.now();
        this.reviewRemark = remark;
    }

    public void reject(Long reviewerId, String reviewerName, String remark) {
        this.status = AuditStatus.REJECT;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewedAt = LocalDateTime.now();
        this.reviewRemark = remark;
    }

    public void incrementSubmitCount() {
        this.submitCount++;
    }
}
