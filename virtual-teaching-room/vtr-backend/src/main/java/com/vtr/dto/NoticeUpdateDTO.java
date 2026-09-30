package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class NoticeUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长200字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 可选封面地址，复用 notice 表已有的 attachment_url 字段。
     */
    private String attachmentUrl;

    private String type;

    private String targetUser;

    private LocalDateTime publishTime;

    private LocalDateTime expireTime;

    private Boolean isTop;
}
