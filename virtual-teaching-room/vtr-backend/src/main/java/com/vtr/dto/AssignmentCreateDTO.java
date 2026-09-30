package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentCreateDTO {

    @NotBlank(message = "作业标题不能为空")
    private String title;

    @NotBlank(message = "作业描述不能为空")
    private String description;

    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;

    // 作业类型: PROGRAMMING 或 TEXT
    private String assignmentType = "PROGRAMMING";

    // 允许的语言列表，如 ["Java", "Python"]
    private List<String> allowedLanguages;

    // 最大提交次数
    private Integer maxSubmitTimes = 5;

    // 时间限制(ms)
    private Integer timeLimit = 1000;

    // 内存限制(MB)
    private Integer memoryLimit = 256;

    // 总分
    private Integer totalScore = 100;

    // 自动测试占比
    private Integer autoTestRatio = 70;

    // 人工评审占比
    private Integer manualReviewRatio = 30;

    // 测试用例列表
    private List<TestCaseDTO> testCases;

    // 从课程题库选入本次作业的题目 ID
    private List<Long> questionIds;

    // ========== 新增字段：发布范围 ==========
    // 发布类型: ALL-发布给所有学生, SELECTED-发布给指定学生
    private String publishType = "ALL";

    // 目标学生ID列表（当 publishType = SELECTED 时必填）
    private List<Long> targetStudentIds;
    private Long courseId;
    private Long classroomId;
}
