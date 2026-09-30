package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teaching_activity", indexes = {
        @Index(name = "idx_organizer_id", columnList = "organizer_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_activity_time", columnList = "activity_time"),
        @Index(name = "idx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TeachingActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "organizer_unit", length = 200)
    private String organizerUnit;

    @Column(name = "sponsor", length = 200)
    private String sponsor;

    @Builder.Default
    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "classroom_id")
    private Long classroomId;

    @Column(name = "activity_time", nullable = false)
    private LocalDateTime activityTime;

    @Column(length = 200)
    private String location;

    @Builder.Default
    private Integer duration = 120;

    @Builder.Default
    @Column(name = "max_participants")
    private Integer maxParticipants = 50;

    @Builder.Default
    @Column(name = "current_participants")
    private Integer currentParticipants = 0;

    @Builder.Default
    @Column(length = 30)
    private String status = "PENDING";

    @Column(name = "registration_deadline")
    private LocalDateTime registrationDeadline;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Builder.Default
    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 活动类型枚举
    public enum ActivityType {
        LECTURE("讲座"),
        SEMINAR("研讨会"),
        LESSON_PREP("集体备课"),
        OPEN_LESSON("公开课"),
        LESSON_OBSERVATION("听评课"),
        TRAINING("专题培训"),
        EXPERIENCE_SHARE("经验分享"),
        OTHER("其他");

        private final String description;

        ActivityType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 活动状态枚举
    public enum ActivityStatus {
        PENDING("待审核"),
        APPROVED("已通过"),
        REJECTED("已拒绝"),
        ONGOING("进行中"),
        ENDED_PENDING_ARCHIVE("已结束待归档"),
        COMPLETED("已完成"),
        CANCELLED("已取消");

        private final String description;

        ActivityStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isExpired() {
        return activityTime != null && activityTime.isBefore(LocalDateTime.now());
    }

    public boolean isFull() {
        return currentParticipants >= maxParticipants;
    }
}
