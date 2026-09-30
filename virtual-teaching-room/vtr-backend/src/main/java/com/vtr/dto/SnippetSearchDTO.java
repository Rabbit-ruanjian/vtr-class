package com.vtr.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class SnippetSearchDTO extends PageQueryDTO {

    private String keyword;

    private String language;

    private List<String> tags;

    private String status;  // 状态筛选 (APPROVED, PENDING, REJECTED)

    private Boolean isPublic;  // 是否公开

    private Long authorId;  // ✅ 新增：作者ID

    private String sortBy = "createdAt"; // createdAt, viewCount, likeCount

    private String sortOrder = "desc"; // asc, desc
}