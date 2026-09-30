package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumPostListVO {
    private Long id;
    private Long authorId;
    private String title;
    private String content;
    private String postType;
    private String audience;
    private Long courseId;
    private String courseName;
    private Long activityId;
    private String imageUrls;
    private String authorName;
    private String authorAvatar;
    private Boolean pinned;
    private LocalDateTime pinnedExpiry;
    private Integer replyCount;
    private Integer views;
    private Integer likeCount;
    private Boolean likedByMe;
    private Boolean solved;
    private LocalDateTime createTime;
    private String auditStatus;

    // 管理员审核用
    private Boolean requestPin;
    private Integer requestPinDays;
}
