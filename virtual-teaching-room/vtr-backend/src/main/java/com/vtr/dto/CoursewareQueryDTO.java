package com.vtr.dto;

import lombok.Data;

@Data
public class CoursewareQueryDTO {
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
    private String visibility;
    private String targetAudience;
    private String fileType;
    private String resourceType;
    private Long courseId;
    private String semester;
    private String chapter;
    private Long sectionId;
    private Long classroomId;
}
