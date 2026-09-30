package com.vtr.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CourseVO {
    private Long id;
    private String courseName;
    private String courseCode;
    private String description;
    private String coverImage;
    private String semester;
    private BigDecimal credits;
    private String courseCategory;
    private String teachingDepartment;
    private String assessmentMethod;
    private Long createdBy;
    private String teacherName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Long> allowedAcademicClassIds;
}
