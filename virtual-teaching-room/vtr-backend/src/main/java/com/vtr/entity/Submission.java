package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vtr.common.BaseEntity;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission", indexes = {
        @Index(name = "idx_assignment", columnList = "assignment_id"),
        @Index(name = "idx_student", columnList = "student_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_submitted", columnList = "submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Submission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"password", "snippets", "submissions", "teachingAssignments"})
    private User student;

    @NotBlank
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String code;

    @NotBlank
    @Column(nullable = false, length = 30)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Lob
    @Column(name = "auto_test_result", columnDefinition = "LONGTEXT")
    private String autoTestResult; // JSON格式存储详细结果

    @Column(name = "auto_test_score")
    private Integer autoTestScore;

    @Lob
    @Column(name = "manual_review", columnDefinition = "TEXT")
    private String manualReview;

    @Column(name = "manual_review_score")
    private Integer manualReviewScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    @JsonIgnoreProperties({"password", "snippets", "submissions"})
    private User reviewer;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "passed_test_count")
    private Integer passedTestCount;

    @Column(name = "total_test_count")
    private Integer totalTestCount;

    @Column(name = "execution_time")
    private Long executionTime; // 总执行时间ms

    @Column(name = "memory_used")
    private Long memoryUsed; // 最大内存使用KB

    @Column(name = "submitted_at")
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "plagiarism_score")
    private Double plagiarismScore; // 抄袭检测分数

    @Column(name = "is_final")
    @Builder.Default
    private Boolean isFinal = false; // 是否为最终提交

    @Column(name = "submit_count")
    @Builder.Default
    private Integer submitCount = 1; // 第几次提交

    @Column(name = "compile_error", columnDefinition = "TEXT")
    private String compileError;

    // 是否已删除（如果需要软删除功能）
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    public enum SubmissionStatus {
        // 原有状态
        SUBMITTED("已提交"),
        QUEUED("排队中"),
        COMPILING("编译中"),
        TESTING("测试中"),
        PASSED("测试通过"),
        FAILED("测试失败"),
        COMPILE_ERROR("编译错误"),
        TIMEOUT("运行超时"),
        MEMORY_LIMIT("内存超限"),
        RUNTIME_ERROR("运行错误"),
        REVIEWED("已评审"),
        PLAGIARISM("疑似抄袭"),

        // ========== 新增状态（供 SubmissionServiceImpl 使用） ==========
        PENDING("待评测"),
        EVALUATING("评测中"),
        GRADED("已评分");

        private final String description;

        SubmissionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // ========== 业务方法 ==========

    public void startTesting() {
        this.status = SubmissionStatus.TESTING;
    }

    public void completeAutoTest(boolean passed, String resultJson,
                                 Integer passedCount, Integer totalCount) {
        this.status = passed ? SubmissionStatus.PASSED : SubmissionStatus.FAILED;
        this.autoTestResult = resultJson;
        this.passedTestCount = passedCount;
        this.totalTestCount = totalCount;
    }

    public void setCompileError(String error) {
        this.status = SubmissionStatus.COMPILE_ERROR;
        this.compileError = error;
    }

    public void completeManualReview(Integer score, String review, User reviewer) {
        this.manualReviewScore = score;
        this.manualReview = review;
        this.reviewer = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.status = SubmissionStatus.REVIEWED;
        this.calculateTotalScore();
    }

    private void calculateTotalScore() {
        if (autoTestScore != null && manualReviewScore != null) {
            Assignment assignment = this.getAssignment();
            int autoWeight = assignment.getAutoTestRatio();
            int manualWeight = assignment.getManualReviewRatio();
            this.totalScore = (autoTestScore * autoWeight + manualReviewScore * manualWeight) / 100;
        }
    }

    public void markAsFinal() {
        this.isFinal = true;
    }

    @Override
    public boolean isEnabled() {
        if (isDeleted != null && isDeleted) {
            return false;
        }
        return true;
    }

    public boolean isPassed() {
        return status == SubmissionStatus.PASSED ||
                status == SubmissionStatus.REVIEWED ||
                status == SubmissionStatus.GRADED;
    }

    public boolean isWaitingForReview() {
        return (status == SubmissionStatus.PASSED || status == SubmissionStatus.EVALUATING)
                && manualReviewScore == null;
    }

    public boolean isCompileError() {
        return status == SubmissionStatus.COMPILE_ERROR;
    }

    public boolean isTimeout() {
        return status == SubmissionStatus.TIMEOUT;
    }
}