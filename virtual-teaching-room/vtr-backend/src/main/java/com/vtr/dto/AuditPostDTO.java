package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class AuditPostDTO {
    @NotBlank(message = "审核状态不能为空")
    private String status;  // APPROVED, REJECTED

    private String remark;

    // 置顶相关字段
    private Boolean pin;     // 是否置顶
    private Integer pinDays; // 置顶天数
}