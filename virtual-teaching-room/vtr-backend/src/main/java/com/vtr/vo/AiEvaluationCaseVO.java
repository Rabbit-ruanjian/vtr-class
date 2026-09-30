package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiEvaluationCaseVO {
    private Long id;
    private Long courseId;
    private String title;
    private String question;
    private String chapter;
    private String requiredKeywords;
    private String forbiddenKeywords;
    private String expectedRoute;
    private Boolean expectedEvidence;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
