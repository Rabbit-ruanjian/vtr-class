package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class AssignTeacherDTO {
    @NotNull(message = "教师ID不能为空")
    private Long teacherId;
}