package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CarouselVO {

    private Long id;

    private String title;

    private String description;

    private String imageUrl;

    private String linkUrl;

    private Integer sortOrder;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer clickCount;

    private String backgroundColor;

    private Boolean isActive;
}