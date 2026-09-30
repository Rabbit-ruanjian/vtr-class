package com.vtr.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Max;
import javax.validation.constraints.Size;

@Data
public class CoursewareCreateDTO {
    @NotBlank(message = "课件标题不能为空")
    private String title;

    @Size(max = 10000, message = "description must not exceed 10000 characters")
    private String description;

    @NotBlank(message = "文件URL不能为空")
    private String fileUrl;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    @NotBlank(message = "资源类型不能为空")
    @Pattern(regexp = "[a-z0-9-]{2,50}", message = "resourceType is invalid")
    private String resourceType;
    private String videoType;
    private String knowledgePoint;
    private Boolean featured;

    @NotNull(message = "请选择所属课程")
    private Long courseId;
    private String semester;
    private String chapter;
    private Long sectionId;
    private String gradeLevel;
    private String version;

    @NotNull(message = "文件大小不能为空")
    @NotNull(message = "fileSize is required")
    @Positive(message = "fileSize must be positive")
    @Max(value = 104857600, message = "fileSize must not exceed 100MB")
    private Long fileSize;

    @NotBlank(message = "可见性不能为空")
    @Pattern(regexp = "PUBLIC|COURSE|PRIVATE|CLASS", message = "visibility is invalid")
    private String visibility;  // PUBLIC, COURSE, PRIVATE, CLASS

    @NotBlank(message = "面向对象不能为空")
    @Pattern(regexp = "ALL|TEACHER|STUDENT", message = "targetAudience is invalid")
    private String targetAudience;  // ALL, TEACHER, STUDENT

    private Long classroomId;  // 班级ID
}
