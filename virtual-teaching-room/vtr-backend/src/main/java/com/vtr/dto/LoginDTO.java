package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginDTO {

    @NotBlank(message = "邮箱或手机号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captcha;

    private String captchaKey;

    // 使用学号登录时用于消除不同学校之间的学号歧义。
    private String schoolCode;
}
