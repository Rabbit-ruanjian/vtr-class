package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SchoolUpsertDTO {
    @NotBlank(message = "学校名称不能为空")
    @Size(max = 100, message = "学校名称不能超过100个字符")
    private String name;

    @Size(max = 30, message = "学校编码不能超过30个字符")
    private String code;

    @Size(max = 100, message = "所属地区不能超过100个字符")
    private String region;
}
