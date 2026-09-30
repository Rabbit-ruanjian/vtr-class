package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignmentVO {

    private Long id;
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
    private String status;
    private Boolean isExpired;

    // 教师信息
    private UserVO teacher;
    private String teacherName;
    private Long teacherId;
    private Long courseId;
    private Long classroomId;
    private String courseName;
    private String courseStatus;
    private Boolean canView;
    private String accessMessage;

    // 测试用例
    private List<TestCaseVO> testCases;

    // 本次作业选择的课程题目 ID
    private List<Long> questionIds;

    // 学生端：我的提交信息
    private Integer mySubmitCount;
    private Integer myRemainingSubmits;
    private SubmissionVO myLastSubmission;

    // 教师端/管理员端：统计信息
    private Long totalSubmissions;
    private Integer totalSubmitCount;
    private Double averageScore;

    // ========== 新增字段：发布范围 ==========
    // 发布类型: ALL-发布给所有学生, SELECTED-发布给指定学生
    private String publishType;

    // 可见学生总数
    private Integer studentCount;

    // 可见学生ID列表（教师/管理员查看时返回）
    private List<Long> visibleStudentIds;

    // 时间戳
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
