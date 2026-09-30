package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SchoolTeacherRosterUpsertDTO {
    @NotBlank(message = "工号不能为空")
    private String employeeNumber;

    private String name;
    private String department;
}
