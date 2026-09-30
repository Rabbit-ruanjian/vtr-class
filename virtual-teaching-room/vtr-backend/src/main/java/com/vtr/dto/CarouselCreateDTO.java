package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class CarouselCreateDTO {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "图片URL不能为空")
    private String imageUrl;

    private String linkUrl;

    private Integer sortOrder = 0;

    private String status = "ACTIVE";

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String backgroundColor;
}