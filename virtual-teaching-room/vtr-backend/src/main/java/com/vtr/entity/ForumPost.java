package com.vtr.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_post")
public class ForumPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // 轻社区内容类型：QUESTION, NOTE, RESOURCE, TEACHING, CASE, RESULT
    @Column(name = "post_type", nullable = false, length = 30)
    private String postType = "QUESTION";

    // 可见范围：ALL, STUDENTS, TEACHERS
    @Column(nullable = false, length = 20)
    private String audience = "ALL";

    // 可选的课程/活动上下文，不建立强外键，避免历史数据和归档数据受影响
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_name", length = 100)
    private String courseName;

    @Column(name = "activity_id")
    private Long activityId;

    // 以 JSON 数组字符串保存图片地址，首版最多展示 6 张
    @Column(name = "image_urls", columnDefinition = "LONGTEXT")
    private String imageUrls;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "author_avatar", length = 500)
    private String authorAvatar;

    @Column(nullable = false)
    private Boolean pinned = false;

    // 置顶过期时间
    @Column(name = "pinned_expiry")
    private LocalDateTime pinnedExpiry;

    // 申请置顶
    @Column(name = "request_pin")
    private Boolean requestPin = false;

    // 申请置顶天数
    @Column(name = "request_pin_days")
    private Integer requestPinDays = 0;

    @Column(name = "reply_count")
    private Integer replyCount = 0;

    @Column
    private Integer views = 0;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(nullable = false)
    private Boolean solved = false;

    // ========== 新增审核字段 ==========
    @Column(name = "audit_status", nullable = false, length = 20)
    private String auditStatus = "PENDING";  // PENDING, APPROVED, REJECTED

    @Column(name = "audit_remark", length = 500)
    private String auditRemark;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "audit_by")
    private Long auditBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

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

    // 判断置顶是否过期
    public boolean isPinnedExpired() {
        if (!pinned) return true;
        if (pinnedExpiry == null) return false;
        return pinnedExpiry.isBefore(LocalDateTime.now());
    }
}
