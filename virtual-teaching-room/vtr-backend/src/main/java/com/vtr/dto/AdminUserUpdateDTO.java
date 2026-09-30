package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

@Data
public class AdminUserUpdateDTO {
    @Size(max = 50, message = "姓名或昵称不能超过50个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;

    private Long schoolId;

    private String identityType;

    @Size(max = 50, message = "学号或工号不能超过50个字符")
    private String identityNumber;

    @Size(max = 200, message = "单位或院系不能超过200个字符")
    private String unitName;

    private Long academicClassId;
}
