// dto/ClassroomCreateDTO.java
package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ClassroomCreateDTO {
    @javax.validation.constraints.NotNull(message = "请选择所属课程")
    private Long courseId;

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 100, message = "班级名称不能超过100个字符")
    private String className;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    private String grade;  // 年级

    private String semester;  // 学期

    @Size(min = 4, max = 16, message = "邀请码长度应为4到16位")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "邀请码只能包含字母和数字")
    private String inviteCode;
}
