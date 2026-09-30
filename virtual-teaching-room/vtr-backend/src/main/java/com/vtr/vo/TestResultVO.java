package com.vtr.vo;

import lombok.Data;

@Data
public class TestResultVO {

    private Long testCaseId;

    private String input;

    private String expectedOutput;

    private String actualOutput;

    private Boolean passed;

    private Long executionTime;

    private Long memoryUsed;

    private String errorMessage;

    private String error;

    private Integer score;

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.error = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : error;
    }
}