package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_participant", indexes = {
        @Index(name = "idx_activity_id", columnList = "activity_id"),
        @Index(name = "idx_teacher_id", columnList = "teacher_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_activity_participant", columnNames = {"activity_id", "teacher_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ActivityParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Builder.Default
    private String status = "CONFIRMED";

    @Column(name = "join_time")
    @Builder.Default
    private LocalDateTime joinTime = LocalDateTime.now();

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "attendance_status", length = 20)
    @Builder.Default
    private String attendanceStatus = "PENDING";

    @Column(name = "attendance_remark", length = 500)
    private String attendanceRemark;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Builder.Default
    private Integer rating = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 参与状态枚举
    public enum ParticipantStatus {
        CONFIRMED("已确认"),
        PENDING("待确认"),
        CANCELLED("已取消");

        private final String description;

        ParticipantStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
