package com.vtr.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_comment")
public class ForumComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    // ========== 新增审核字段 ==========
    @Column(name = "audit_status", nullable = false, length = 20)
    private String auditStatus = "PENDING";  // PENDING, APPROVED, REJECTED

    @Column(name = "audit_remark", length = 500)
    private String auditRemark;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "audit_by")
    private Long auditBy;

    // ========== 审核业务方法 ==========
    public void approve(Long auditorId, String remark) {
        this.auditStatus = "APPROVED";
        this.auditBy = auditorId;
        this.auditTime = LocalDateTime.now();
        this.auditRemark = remark;
    }

    public void reject(Long auditorId, String remark) {
        this.auditStatus = "REJECTED";
        this.auditBy = auditorId;
        this.auditTime = LocalDateTime.now();
        this.auditRemark = remark;
    }

    public boolean isApproved() {
        return "APPROVED".equals(this.auditStatus);
    }

    public boolean isPending() {
        return "PENDING".equals(this.auditStatus);
    }
}
