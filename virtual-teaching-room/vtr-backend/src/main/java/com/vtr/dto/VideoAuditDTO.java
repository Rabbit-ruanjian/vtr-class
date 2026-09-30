package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class VideoAuditDTO {
    @NotBlank
    private String action;

    @Size(max = 1000)
    private String remark;
}
