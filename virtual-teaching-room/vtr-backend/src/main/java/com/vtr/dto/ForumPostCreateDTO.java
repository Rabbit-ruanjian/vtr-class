package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ForumPostCreateDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @Size(max = 30, message = "内容类型不合法")
    private String postType = "QUESTION";

    @Size(max = 20, message = "可见范围不合法")
    private String audience = "ALL";

    private Long courseId;
    private String courseName;
    private Long activityId;

    // JSON 数组字符串，例如 ["/uploads/images/2026/09/a.png"]
    private String imageUrls;

    // 申请置顶
    private Boolean requestPin = false;

    // 申请置顶天数
    private Integer pinDays = 1;
}
