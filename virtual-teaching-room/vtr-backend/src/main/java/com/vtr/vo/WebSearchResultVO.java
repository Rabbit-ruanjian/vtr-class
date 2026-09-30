package com.vtr.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 统一的联网搜索结果结构，后续可适配不同搜索服务。 */
@Data
@AllArgsConstructor
public class WebSearchResultVO {
    private String title;
    private String url;
    private String snippet;
    private String provider;
}
