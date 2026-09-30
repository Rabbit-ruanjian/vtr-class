package com.vtr.dto;

import lombok.Data;

@Data
public class TestCaseDTO {

    private Long id;

    // 移除 @NotBlank，允许虚拟测试用例的 input 为空
    private String input;

    // 移除 @NotBlank，允许虚拟测试用例的 expectedOutput 为空
    private String expectedOutput;

    private String description;

    private Boolean isPublic = true;

    private Integer score = 10;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Boolean isSample = false;

    // 新增：是否为虚拟测试用例
    private Boolean isVirtual = false;
}