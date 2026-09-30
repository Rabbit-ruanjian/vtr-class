package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiAgentArtifactVO {
    private Long id;
    private Long courseId;
    private Long createdBy;
    private String taskType;
    private String title;
    private String content;
    private String status;
    private String reviewRemark;
    private LocalDateTime createdAt;
    private List<AiSourceVO> sources;
    /** 与普通 AI 回答统一的追踪和质量元数据。 */
    private String requestId;
    private String route;
    private String answerMode;
    private String confidence;
    private Boolean hasEvidence;
    private Long latencyMs;
    private String retrievalMode;
    private Integer retrievedChunkCount;
    private String qualityStatus;
}
