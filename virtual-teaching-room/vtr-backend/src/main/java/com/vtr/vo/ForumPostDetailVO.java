package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ForumPostDetailVO {
    private Long id;
    private String title;
    private String content;
    private String postType;
    private String audience;
    private Long courseId;
    private String courseName;
    private Long activityId;
    private String imageUrls;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private Boolean pinned;
    private LocalDateTime pinnedExpiry;  // 置顶过期时间
    private Integer replyCount;
    private Integer views;
    private Integer likeCount;
    private Boolean likedByMe;
    private Boolean solved;
    private LocalDateTime createTime;
    private List<ForumCommentVO> comments;
    private Boolean requestPin;
    private Integer requestPinDays;
    private String auditStatus;
    private String auditRemark;

}
