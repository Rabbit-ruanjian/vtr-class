package com.vtr.dto;

import lombok.Data;
import java.util.List;

@Data
public class CodeExecutionRequest {
    private String code;
    private String language;
    private Long assignmentId;
    private List<TestCaseDTO> testCases;
}