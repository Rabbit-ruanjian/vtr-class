package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class SubmissionDTO {

    @NotNull(message = "作业ID不能为空")
    private Long assignmentId;

    // 代码或文本内容（编程作业存代码，文本作业存内容）
    private String code;

    // 编程语言（编程作业必填，文本作业为 "TEXT"）
    private String language;

    // 文本作业内容（兼容字段，与code等价）
    private String content;

    // 判断是否为文本作业提交
    public boolean isTextSubmission() {
        return "TEXT".equalsIgnoreCase(language);
    }

    // 获取实际提交内容（优先使用content，其次使用code）
    public String getActualContent() {
        if (content != null && !content.trim().isEmpty()) {
            return content;
        }
        return code;
    }
}