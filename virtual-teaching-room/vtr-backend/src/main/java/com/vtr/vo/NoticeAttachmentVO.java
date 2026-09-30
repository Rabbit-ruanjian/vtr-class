package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeAttachmentVO {
    private Long id;
    private Long noticeId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private Long uploadBy;
    private LocalDateTime createdAt;
}
