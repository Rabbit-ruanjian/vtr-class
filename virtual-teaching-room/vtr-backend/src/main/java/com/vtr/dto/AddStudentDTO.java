// AddStudentDTO.java
package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class AddStudentDTO {
    private Long studentId;
    private String studentNumber;
    private Long classroomId;  // 新增：班级ID
}