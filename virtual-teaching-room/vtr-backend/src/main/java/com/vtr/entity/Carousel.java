package com.vtr.entity;

import com.vtr.common.BaseEntity;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

@Entity
@Table(name = "carousel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Carousel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String linkUrl;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CarouselStatus status = CarouselStatus.ACTIVE;

    @Column(name = "start_time")
    private java.time.LocalDateTime startTime;

    @Column(name = "end_time")
    private java.time.LocalDateTime endTime;

    @Column(name = "click_count")
    @Builder.Default
    private Integer clickCount = 0;

    @Column(name = "background_color", length = 20)
    private String backgroundColor;

    public enum CarouselStatus {
        ACTIVE("启用"),
        INACTIVE("禁用");

        private final String description;

        CarouselStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 业务方法
    public boolean isActive() {
        if (status != CarouselStatus.ACTIVE) return false;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) return false;
        if (endTime != null && now.isAfter(endTime)) return false;

        return true;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }
}