package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SendVerificationCodeDTO {

    @NotBlank(message = "验证码渠道不能为空")
    private String channel;

    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    private String scene = "LOGIN";
}
