package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CourseChapterUpsertDTO {
    @NotBlank(message = "章节名称不能为空")
    @Size(max = 100, message = "章节名称不能超过100个字符")
    private String title;

    @Size(max = 200, message = "章节副标题不能超过200个字符")
    private String subtitle;

    @Size(max = 500, message = "章节说明不能超过500个字符")
    private String description;

    private Integer sortOrder;
}
