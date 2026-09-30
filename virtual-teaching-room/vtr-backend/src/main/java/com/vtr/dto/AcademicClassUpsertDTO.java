package com.vtr.dto;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
@Data public class AcademicClassUpsertDTO {
    private Long schoolId;
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Size(max = 100) private String college;
    @Size(max = 100) private String campus;
    @NotBlank @Size(max = 100) private String major;
    @NotBlank @Size(max = 30) private String grade;
    @Size(max = 50) private String classCode;
    @Size(max = 50) private String headTeacherName;
    @Size(max = 50) private String counselorName;
}
