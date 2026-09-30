package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 联网搜索请求。当前只定义协议，搜索执行由具体 provider 负责。 */
@Data
public class WebSearchRequest {

    @NotBlank(message = "搜索内容不能为空")
    @Size(max = 500, message = "搜索内容不能超过 500 个字符")
    private String query;
}
