package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CodeSnippetVO {
    private Long id;
    private String title;
    private String description;
    private String code;
    private String language;
    private List<String> tags;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer downloadCount;
    private Boolean isPublic;
    private Boolean isLiked;
    private Boolean isAuthor;

    // ========== 时间字段（关键修复） ==========
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间

    // 作者信息
    private UserVO author;
    private String authorName;      // 作者名称
    private String authorAvatar;    // 作者头像
}