package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClassroomVO {
    private Long id;
    private Long courseId;
    private String className;
    private String description;
    private String grade;
    private String semester;
    private String inviteCode;
    private Integer studentCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 新增字段
    private Long teacherId;
    private String teacherName;
}
