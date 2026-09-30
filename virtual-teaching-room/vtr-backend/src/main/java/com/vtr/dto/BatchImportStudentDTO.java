// BatchImportStudentDTO.java
package com.vtr.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchImportStudentDTO {
    private List<String> studentNumbers;
    private Long classroomId;  // 新增：班级ID
}