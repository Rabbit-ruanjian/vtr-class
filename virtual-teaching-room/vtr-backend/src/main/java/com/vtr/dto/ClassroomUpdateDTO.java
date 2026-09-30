package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.Size;

@Data
public class ClassroomUpdateDTO {
    private Long courseId;

    @Size(max = 100, message = "班级名称不能超过100个字符")
    private String className;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    private String grade;

    private String semester;
}
