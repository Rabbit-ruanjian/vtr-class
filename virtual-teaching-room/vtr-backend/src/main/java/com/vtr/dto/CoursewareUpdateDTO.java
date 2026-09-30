package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Max;

@Data
public class CoursewareUpdateDTO {
    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;
    @Size(max = 10000, message = "description must not exceed 10000 characters")
    private String description;
    @Size(max = 500, message = "fileUrl must not exceed 500 characters")
    private String fileUrl;
    @Size(max = 100, message = "fileName must not exceed 100 characters")
    private String fileName;
    @Size(max = 50, message = "fileType must not exceed 50 characters")
    private String fileType;
    @Positive(message = "fileSize must be positive")
    @Max(value = 104857600, message = "fileSize must not exceed 100MB")
    private Long fileSize;
    @Pattern(regexp = "[a-z0-9-]{2,50}", message = "resourceType is invalid")
    private String resourceType;
    private String videoType;
    private String knowledgePoint;
    private Boolean featured;
    private Long courseId;
    private String semester;
    private String chapter;
    private Long sectionId;
    private String gradeLevel;
    private String version;
    @Pattern(regexp = "PUBLIC|COURSE|PRIVATE|CLASS", message = "visibility is invalid")
    private String visibility;
    @Pattern(regexp = "ALL|TEACHER|STUDENT", message = "targetAudience is invalid")
    private String targetAudience;
    private Long classroomId;
    /** AI 生成/更新的教学大纲无需管理员审核，直接发布。 */
    private Boolean aiGenerated;
}
