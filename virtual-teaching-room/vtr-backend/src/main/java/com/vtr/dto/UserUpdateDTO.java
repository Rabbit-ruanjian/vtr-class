package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.Email;

@Data
public class UserUpdateDTO {

    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String avatar;

    private String oldPassword;

    private String newPassword;
}