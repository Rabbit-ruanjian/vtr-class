package com.vtr.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentUpdateDTO {

    private String title;

    private String description;

    private LocalDateTime deadline;

    private String assignmentType;

    private List<String> allowedLanguages;

    private Integer maxSubmitTimes;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer totalScore;

    private Integer autoTestRatio;

    private Integer manualReviewRatio;

    private List<TestCaseDTO> testCases;

    // 从课程题库选入本次作业的题目 ID
    private List<Long> questionIds;

    private String status;

    // ========== 新增字段：发布范围 ==========
    private String publishType;

    // 目标学生ID列表（当 publishType = SELECTED 时使用）
    private List<Long> targetStudentIds;
}
