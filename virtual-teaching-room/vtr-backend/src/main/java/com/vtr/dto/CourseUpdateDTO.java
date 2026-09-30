package com.vtr.dto;

import lombok.Data;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class CourseUpdateDTO {
    private Long schoolId;
    @Size(max = 100, message = "课程名称不能超过100个字符")
    private String courseName;

    @Size(max = 50, message = "课程代码不能超过50个字符")
    private String courseCode;

    @Size(max = 500, message = "课程简介不能超过500个字符")
    private String description;

    @Size(max = 500, message = "课程封面地址不能超过500个字符")
    private String coverImage;

    @Size(max = 50, message = "学期不能超过50个字符")
    private String semester;

    @DecimalMin(value = "0.0", inclusive = false, message = "学分必须大于0")
    @Digits(integer = 3, fraction = 1, message = "学分最多保留1位小数")
    private BigDecimal credits;

    @Size(max = 50, message = "课程类别不能超过50个字符")
    private String courseCategory;

    @Size(max = 100, message = "开课院系不能超过100个字符")
    private String teachingDepartment;

    @Size(max = 50, message = "考核方式不能超过50个字符")
    private String assessmentMethod;

    private List<Long> allowedAcademicClassIds;
}
