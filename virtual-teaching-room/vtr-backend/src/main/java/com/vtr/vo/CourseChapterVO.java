package com.vtr.vo;

import lombok.Data;

@Data
public class CourseChapterVO {
    private Long chapterId;
    private Long courseId;
    private String title;
    private String subtitle;
    private String description;
    private Integer sortOrder;
    private String status;
    private long resourceCount;
    private long questionCount;
}
