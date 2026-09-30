package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ManualReviewDTO {

    @NotNull(message = "分数不能为空")
    private Integer score;

    @Size(max = 2000, message = "评审意见最长2000字符")
    private String comment;

    private String codeComments; // JSON格式，行级评论

    private Boolean isFinal = true;
}