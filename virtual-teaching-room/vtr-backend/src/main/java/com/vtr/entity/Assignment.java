package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vtr.common.BaseEntity;
import lombok.*;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "assignment", indexes = {
        @Index(name = "idx_teacher", columnList = "teacher_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_deadline", columnList = "deadline")
})
@Where(clause = "is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Assignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnoreProperties({"password", "teachingAssignments", "submissions"})
    private User teacher;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "classroom_id")
    private Long classroomId;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "max_submit_times")
    @Builder.Default
    private Integer maxSubmitTimes = 5;

    @Column(name = "time_limit")
    @Builder.Default
    private Integer timeLimit = 1000; // 毫秒

    @Column(name = "memory_limit")
    @Builder.Default
    private Integer memoryLimit = 128; // MB

    @Column(name = "allowed_languages", length = 200)
    @Builder.Default
    private String allowedLanguages = "java,python,cpp,javascript";

    @Lob
    @Column(name = "template_code", columnDefinition = "TEXT")
    private String templateCode;

    @Lob
    @Column(name = "reference_solution", columnDefinition = "TEXT")
    private String referenceSolution;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Submission> submissions = new ArrayList<>();

    @Column(name = "total_score")
    @Builder.Default
    private Integer totalScore = 100;

    @Column(name = "auto_test_ratio")
    @Builder.Default
    private Integer autoTestRatio = 70; // 自动测试占比70%

    @Column(name = "manual_review_ratio")
    @Builder.Default
    private Integer manualReviewRatio = 30; // 人工评审占比30%

    // 作业类型：PROGRAMMING（编程题）或 TEXT（文本题）
    @Column(name = "assignment_type", length = 20)
    @Builder.Default
    private String assignmentType = "PROGRAMMING";

    // 课程题库中被本次作业选用的题目 ID，使用逗号分隔保存以兼容已有作业数据。
    @Lob
    @Column(name = "question_ids", columnDefinition = "TEXT")
    private String questionIds;

    // 是否已删除 - 使用 boolean 基本类型，默认 false
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "publish_type", length = 20)
    @Builder.Default
    private String publishType = "ALL"; // ALL: 全部学生, SELECTED: 指定学生

    @Column(name = "student_count")
    @Builder.Default
    private Integer studentCount = 0;

    public enum AssignmentStatus {
        DRAFT("草稿"),
        PENDING("待审核"),
        PUBLISHED("已发布"),
        CLOSED("已关闭");

        private final String description;

        AssignmentStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // ========== 业务方法 ==========

    public void addTestCase(TestCase testCase) {
        testCases.add(testCase);
        testCase.setAssignment(this);
    }

    public void removeTestCase(TestCase testCase) {
        testCases.remove(testCase);
        testCase.setAssignment(null);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(deadline);
    }

    public boolean isPublished() {
        return status == AssignmentStatus.PUBLISHED;
    }

    public boolean isLanguageAllowed(String language) {
        if (allowedLanguages == null || allowedLanguages.trim().isEmpty()) {
            return false;
        }
        return allowedLanguages.toLowerCase().contains(language.toLowerCase());
    }

    /**
     * 获取允许的语言列表
     * 增加空值处理，避免 NullPointerException
     */
    public List<String> getAllowedLanguageList() {
        // 空值处理：如果 allowedLanguages 为 null 或空字符串，返回空列表
        if (allowedLanguages == null || allowedLanguages.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(allowedLanguages.split(","));
    }

    /**
     * 判断是否为文本作业（无需自动测试）
     */
    public boolean isTextAssignment() {
        return "TEXT".equals(assignmentType);
    }

    /**
     * 判断是否为编程作业（需要自动测试）
     */
    public boolean isProgrammingAssignment() {
        return "PROGRAMMING".equals(assignmentType);
    }

    // ✅ 实现 BaseEntity 的抽象方法 isEnabled()
    @Override
    public boolean isEnabled() {
        // 作业启用的条件：
        // 1. 未被删除
        // 2. 状态为已发布

        if (isDeleted) {
            return false;
        }

        if (status == null) {
            return false;
        }

        // 只有已发布的作业才算启用
        return status == AssignmentStatus.PUBLISHED;
    }

    public String getPublishType() { return publishType; }
    public void setPublishType(String publishType) { this.publishType = publishType; }

    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }

    // 判断是否为发布给特定学生
    public boolean isSelectedPublish() {
        return "SELECTED".equals(publishType);
    }
}
