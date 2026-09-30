package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionVO {

    private Long id;

    private Long assignmentId;

    private String assignmentTitle;

    private UserVO student;

    // ========== 新增字段 ==========
    private Long studentId;

    private String studentName;

    private String code;

    private String language;

    private String status;

    private String statusDescription;

    private Integer autoTestScore;

    private Integer manualReviewScore;

    private Integer totalScore;

    private Integer score;

    private String manualReview;

    private String feedback;

    private UserVO reviewer;

    private Integer passedTestCount;

    private Integer totalTestCount;

    private Long executionTime;

    private Long memoryUsed;

    private LocalDateTime submittedAt;

    private LocalDateTime submitTime;

    private Boolean isFinal;

    private Integer submitCount;

    private String compileError;

    private String result;

    private Double plagiarismScore;

    // ========== 新增字段 ==========
    private List<TestResultVO> testResults;  // 测试用例结果列表
}