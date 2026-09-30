package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumCommentVO {
    private Long id;
    private String content;
    private Long authorId;
    private String authorName;
    private LocalDateTime createTime;
    private Long postId;           // 新增：所属帖子ID
    private String auditStatus;    // 新增：审核状态
}
