package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String organizerUnit;
    private String sponsor;
    private Boolean isPinned;
    private String content;
    private String type;
    private String typeName;
    private Long organizerId;
    private ActivityOrganizerVO organizer;
    private LocalDateTime activityTime;
    private String location;
    private Integer duration;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String status;
    private String statusName;
    private String rejectReason;
    private String cancelReason;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 扩展字段
    private Integer participantCount;
    private Boolean isJoined;
    private Boolean isOrganizer;
    private Boolean hasCheckedIn;  // 是否已签到
    private Boolean isFull;        // 是否已满员
    private List<UserVO> participants;
    private Long discussionCount;

    // 计算方法
    public boolean isExpired() {
        return activityTime != null && activityTime.isBefore(LocalDateTime.now());
    }

    public boolean isFull() {
        return participantCount != null && maxParticipants != null && participantCount >= maxParticipants;
    }

    public boolean isOngoing() {
        if (activityTime == null) return false;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = activityTime.plusMinutes(duration != null ? duration : 120);
        return now.isAfter(activityTime) && now.isBefore(endTime);
    }

    public LocalDateTime getEndTime() {
        if (activityTime == null) return null;
        return activityTime.plusMinutes(duration != null ? duration : 120);
    }
}
