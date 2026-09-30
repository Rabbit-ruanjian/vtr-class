package com.vtr.vo;

import lombok.Data;

@Data
public class SubmissionProgressVO {

    private Long submissionId;

    private String status;

    private Integer progress; // 0-100

    private String message;

    private TestResultVO currentTest;

    private Long elapsedTime;

    private Long estimatedTotalTime;

    private Integer score;

    private Long executionTime;

    private Long memoryUsed;

    private Integer passedTestCount;

    private Integer totalTestCount;

    public void setScore(Integer score) {
        this.score = score;
    }
}