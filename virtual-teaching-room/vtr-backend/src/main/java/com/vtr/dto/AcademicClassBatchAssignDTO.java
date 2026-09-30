package com.vtr.dto;
import lombok.Data;
import javax.validation.Valid;
import java.util.List;
@Data
public class AcademicClassBatchAssignDTO {
    @Valid private List<AcademicClassStudentImportDTO> students;
    private List<String> studentNumbers;
}
