package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

@Entity
@Table(name = "test_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    @JsonIgnore
    private Assignment assignment;

    // 移除 @NotBlank，允许虚拟测试用例的 input 为空
    @Lob
    @Column(name = "input_data", columnDefinition = "TEXT")
    private String input;

    // 移除 @NotBlank，允许虚拟测试用例的 expectedOutput 为空
    @Lob
    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(length = 255)
    private String description;

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "score")
    @Builder.Default
    private Integer score = 10;

    @Column(name = "time_limit")
    @Builder.Default
    private Integer timeLimit = 1000; // 毫秒，覆盖作业设置

    @Column(name = "memory_limit")
    @Builder.Default
    private Integer memoryLimit = 128; // MB，覆盖作业设置

    @Column(name = "is_sample")
    @Builder.Default
    private Boolean isSample = false; // 是否为样例（展示给学生）

    // 新增：是否为虚拟测试用例（用于不支持自动评测的语言）
    @Column(name = "is_virtual")
    @Builder.Default
    private Boolean isVirtual = false;
}