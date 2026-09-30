package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscussionVO {

    private Long id;
    private Long activityId;
    private Long teacherId;
    private UserVO teacher;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 扩展字段
    private List<DiscussionVO> replies;
    private Integer replyCount;
}