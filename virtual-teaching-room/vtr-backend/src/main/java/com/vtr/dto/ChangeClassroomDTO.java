package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ChangeClassroomDTO {
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotNull(message = "目标班级ID不能为空")
    private Long newClassroomId;
}