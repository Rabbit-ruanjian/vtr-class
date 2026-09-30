package com.vtr.vo;

import lombok.Data;

@Data
public class AiEvaluationResultVO {
    private Long caseId;
    private String title;
    private String question;
    private String expectedRoute;
    private String actualRoute;
    private Boolean routePassed;
    private Boolean evidencePassed;
    private String missingKeywords;
    private String forbiddenKeywordsFound;
    private Boolean passed;
    private String answerExcerpt;
    private Long latencyMs;
}
