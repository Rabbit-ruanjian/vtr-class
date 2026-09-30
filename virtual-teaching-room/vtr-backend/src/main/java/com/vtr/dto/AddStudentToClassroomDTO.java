// dto/AddStudentToClassroomDTO.java
package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class AddStudentToClassroomDTO {
    @NotNull(message = "班级ID不能为空")
    private Long classroomId;

    private List<Long> studentIds;  // 学生ID列表

    private List<String> studentNumbers;  // 学号列表（二选一）
}