package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ActivityReviewDTO {

    @NotBlank(message = "审核结果不能为空")
    private String action; // approve, reject

    @Size(max = 500, message = "拒绝原因不能超过500字")
    private String rejectReason;
}
