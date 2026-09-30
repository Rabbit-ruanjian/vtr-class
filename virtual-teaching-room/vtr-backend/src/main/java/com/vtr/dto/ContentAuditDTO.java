package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ContentAuditDTO {

    @NotBlank(message = "审核结果不能为空")
    private String action; // PASS or REJECT

    private String remark;
}