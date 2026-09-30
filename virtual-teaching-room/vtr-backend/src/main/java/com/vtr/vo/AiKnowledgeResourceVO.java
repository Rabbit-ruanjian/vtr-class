package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiKnowledgeResourceVO {
    private Long id;
    private Long courseId;
    private String documentName;
    private String chapter;
    private String sourceType;
    private String sourceUrl;
    private String license;
    private String sourceAuthor;
    private String attribution;
    private String fileUrl;
    private Long fileSize;
    private Integer textLength;
    private Integer chunkCount;
    private String status;
    private Long coursewareId;
    private String indexMode;
    private String reviewRemark;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
