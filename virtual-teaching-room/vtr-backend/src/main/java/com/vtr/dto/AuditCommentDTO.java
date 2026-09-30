package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class AuditCommentDTO {
    @NotBlank(message = "审核状态不能为空")
    private String status;  // APPROVED, REJECTED

    private String remark;
}