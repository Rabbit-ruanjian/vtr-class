package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前 AI 配置的联网搜索能力检测结果。 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSearchCapabilityVO {
    private boolean supported;
    private String provider;
    private String model;
    private String message;
}
