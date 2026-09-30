package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoticeVO {
    private Long id;
    private String title;
    private String content;
    private String attachmentUrl;
    private String type;
    private String typeDescription;
    private String status;
    private String targetUser;
    private String targetUserDescription;
    private Long authorId;
    private String authorName;
    private String authorNickname;
    private LocalDateTime publishTime;
    private LocalDateTime expireTime;
    private Integer viewCount;
    private Boolean isTop;
    private Boolean isRead;
    private UserVO author;  // 或者使用 UserVO 对象
    private List<NoticeAttachmentVO> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
