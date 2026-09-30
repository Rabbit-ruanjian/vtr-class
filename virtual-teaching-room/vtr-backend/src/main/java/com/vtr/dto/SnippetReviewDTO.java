package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class SnippetReviewDTO {

    @NotNull(message = "审核结果不能为空")
    private String action; // APPROVE or REJECT

    private String remark;
}