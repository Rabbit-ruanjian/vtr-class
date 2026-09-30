package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Payload for a teaching outline created from the structured node editor. */
@Data
public class StructuredOutlineCreateDTO {

    @NotBlank(message = "教学大纲标题不能为空")
    @Size(max = 200, message = "教学大纲标题不能超过 200 个字符")
    private String title;

    @Size(max = 10000, message = "description must not exceed 10000 characters")
    private String description;

    @NotNull(message = "请选择所属课程")
    private Long courseId;

    private String chapter;

    private Long sectionId;

    @NotBlank(message = "可见性不能为空")
    @Pattern(regexp = "PUBLIC|COURSE|PRIVATE|CLASS", message = "visibility is invalid")
    private String visibility;

    @NotBlank(message = "面向对象不能为空")
    @Pattern(regexp = "ALL|TEACHER|STUDENT", message = "targetAudience is invalid")
    private String targetAudience;

    private Long classroomId;

    /** AI 生成的教学大纲无需管理员审核，提交后直接发布。 */
    private boolean aiGenerated;
}
