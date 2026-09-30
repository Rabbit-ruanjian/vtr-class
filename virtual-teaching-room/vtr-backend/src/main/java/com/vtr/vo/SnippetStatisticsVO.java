// src/main/java/com/vtr/vo/SnippetStatisticsVO.java
package com.vtr.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SnippetStatisticsVO {

    private Long totalSnippets;

    private Long approvedSnippets;

    private Long pendingSnippets;

    private Map<String, Long> languageDistribution;

    private List<String> hotTags;

    private List<CodeSnippetVO> popularSnippets;
}