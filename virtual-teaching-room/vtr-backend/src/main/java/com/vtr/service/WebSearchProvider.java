package com.vtr.service;

import com.vtr.vo.WebSearchCapabilityVO;
import com.vtr.vo.WebSearchResultVO;

import java.util.List;

/**
 * 联网搜索提供商统一接口。
 *
 * <p>后续可以接入 Kimi 内置搜索、Tavily、Bing 等服务，业务层只依赖这个接口。</p>
 */
public interface WebSearchProvider {

    String getProviderName();

    WebSearchCapabilityVO detectCapability();

    List<WebSearchResultVO> search(String query);
}
