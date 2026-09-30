package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CodeLoginDTO {

    @NotBlank(message = "验证码渠道不能为空")
    private String channel;

    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 注册页可选提交密码。普通验证码登录不提交此字段；只有首次通过验证码创建账号时使用。
     */
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String password;
}
