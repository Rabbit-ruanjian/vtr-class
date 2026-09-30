package com.vtr.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Data
public class SchoolAcademicStructureDTO {
    @Valid
    private List<Department> departments = new ArrayList<>();

    @Data
    public static class Department {
        @NotBlank(message = "院系名称不能为空")
        @Size(max = 100, message = "院系名称不能超过100个字符")
        private String name;

        @Valid
        private List<Major> majors = new ArrayList<>();
    }

    @Data
    public static class Major {
        @NotBlank(message = "专业名称不能为空")
        @Size(max = 100, message = "专业名称不能超过100个字符")
        private String name;
    }
}
