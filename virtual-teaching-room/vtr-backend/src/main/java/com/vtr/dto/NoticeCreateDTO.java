package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class NoticeCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长200字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotNull(message = "公告类型不能为空")
    private String type; // URGENT, IMPORTANT, NORMAL, SYSTEM

    private String targetUser = "ALL"; // ALL, STUDENTS, TEACHERS, ADMINS

    private LocalDateTime publishTime;

    private LocalDateTime expireTime;

    private Boolean isTop = false;

    private String attachmentUrl;
}