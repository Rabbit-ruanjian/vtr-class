package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** AI 回答反馈请求。requestId 由服务端返回，避免客户端伪造回答内容。 */
@Data
public class AiFeedbackRequest {

    @NotBlank(message = "回答追踪 ID 不能为空")
    @Size(max = 64, message = "回答追踪 ID 无效")
    private String requestId;

    @NotNull(message = "请提供是否有帮助")
    private Boolean helpful;

    @Size(max = 30, message = "反馈类型不能超过 30 个字符")
    private String feedbackType;

    @Size(max = 1000, message = "反馈说明不能超过 1000 个字符")
    private String comment;
}
