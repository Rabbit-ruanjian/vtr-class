package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class SnippetUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长200字符")
    private String title;

    private String description;

    @NotBlank(message = "代码不能为空")
    private String code;

    @NotBlank(message = "编程语言不能为空")
    private String language;

    @Size(max = 10, message = "最多10个标签")
    private List<String> tags;

    private Boolean isPublic;
}