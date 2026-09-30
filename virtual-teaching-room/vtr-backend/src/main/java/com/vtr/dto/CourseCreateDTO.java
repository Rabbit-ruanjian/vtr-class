package com.vtr.dto;
import lombok.Data;
import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Size;
import java.util.List;
@Data
public class CourseCreateDTO {
    private Long schoolId;
    @NotBlank @Size(max = 100) private String courseName;
    @Size(max = 50) private String courseCode;
    @Size(max = 500) private String description;
    @Size(max = 500) private String coverImage;
    @Size(max = 50) private String semester;
    @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 3, fraction = 1) private BigDecimal credits;
    @Size(max = 50) private String courseCategory;
    @Size(max = 100) private String teachingDepartment;
    @Size(max = 50) private String assessmentMethod;
    private List<Long> allowedAcademicClassIds;
}
