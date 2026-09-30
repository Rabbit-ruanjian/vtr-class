package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class BatchReviewDTO {

    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;

    @NotNull(message = "操作类型不能为空")
    private String action; // APPROVE or REJECT

    private String reason;
}