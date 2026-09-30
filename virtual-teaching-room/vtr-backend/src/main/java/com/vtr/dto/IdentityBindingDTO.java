package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class IdentityBindingDTO {

    private Long schoolId;

    @Size(max = 200, message = "单位名称不能超过200个字符")
    private String unitName;

    private String identityType;

    @Size(max = 50, message = "学号或工号不能超过50个字符")
    private String identityNumber;
}
