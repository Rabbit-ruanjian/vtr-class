package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class UserRegisterDTO {

    private Long schoolId;

    // 账号由邮箱或手机号承担；保留该字段兼容旧版接口，允许为空。
    @Size(min = 3, max = 20, message = "用户名长度3-20位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @NotBlank(message = "注册验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "注册验证码必须是6位数字")
    private String code;

    private String unitName;

    private String identityType;

    private String identityNumber;

    private String role = "STUDENT";

    private String avatar;
}
