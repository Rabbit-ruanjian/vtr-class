package com.vtr.vo;

import lombok.Data;

@Data
public class TestCaseVO {

    private Long id;

    private String input;

    private String expectedOutput;

    private String description;

    private Boolean isPublic;

    private Integer score;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Boolean isSample;

    // 新增：是否为虚拟测试用例
    private Boolean isVirtual;
}