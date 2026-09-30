package com.vtr.entity;

import com.vtr.common.BaseEntity;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "notice", indexes = {
        @Index(name = "idx_type", columnList = "type"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_publish", columnList = "publish_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NoticeType type = NoticeType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NoticeStatus status = NoticeStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TargetUser targetUser = TargetUser.ALL;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "is_top")
    @Builder.Default
    private Boolean isTop = false;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    public enum NoticeType {
        URGENT("紧急"),
        IMPORTANT("重要"),
        NORMAL("普通"),
        SYSTEM("系统");

        private final String description;

        NoticeType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum NoticeStatus {
        DRAFT("草稿"),
        PUBLISHED("已发布"),
        WITHDRAWN("已撤回");

        private final String description;

        NoticeStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TargetUser {
        ALL("所有人"),
        STUDENTS("仅学生"),
        TEACHERS("仅教师"),
        ADMINS("仅管理员");

        private final String description;

        TargetUser(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // ========== 业务方法 ==========

    public void publish() {
        this.status = NoticeStatus.PUBLISHED;
        this.publishTime = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = NoticeStatus.WITHDRAWN;
    }

    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    public boolean isPublished() {
        return status == NoticeStatus.PUBLISHED &&
                publishTime != null &&
                !LocalDateTime.now().isBefore(publishTime) &&
                !isExpired();
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    // ✅ 实现 BaseEntity 的抽象方法 isEnabled()
    @Override
    public boolean isEnabled() {
        if (isDeleted != null && isDeleted) {
            return false;
        }

        if (status == null) {
            return false;
        }

        if (status != NoticeStatus.PUBLISHED) {
            return false;
        }

        if (isExpired()) {
            return false;
        }

        if (publishTime != null && LocalDateTime.now().isBefore(publishTime)) {
            return false;
        }

        return true;
    }

    // ✅ 添加辅助方法（不依赖 User 的 hasRole）
    public boolean isTargetUserAll() {
        return targetUser == TargetUser.ALL;
    }

    public boolean isTargetUserStudents() {
        return targetUser == TargetUser.STUDENTS;
    }

    public boolean isTargetUserTeachers() {
        return targetUser == TargetUser.TEACHERS;
    }

    public boolean isTargetUserAdmins() {
        return targetUser == TargetUser.ADMINS;
    }

    public void top() {
        this.isTop = true;
    }

    public void unTop() {
        this.isTop = false;
    }
}
