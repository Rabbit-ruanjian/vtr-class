package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CoursewareVO {
    private Long id;
    private String title;
    private String description;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private String resourceType;
    private String videoType;
    private String knowledgePoint;
    private Boolean featured;
    private String auditRemark;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long previousVersionId;
    private Long versionGroupId;
    private Integer versionNumber;
    private Long courseId;
    private String courseName;
    private String semester;
    private String chapter;
    private Long sectionId;
    private String gradeLevel;
    private String version;
    private Long fileSize;
    private Long teacherId;
    private String teacherName;
    private String visibility;
    private String targetAudience;
    private Long classroomId;
    private String className;
    private Integer downloadCount;
    private Integer viewCount;
    private String status;
    private String aiIndexStatus;
    private String aiIndexMessage;
    private Long aiDocumentId;
    private LocalDateTime aiIndexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
