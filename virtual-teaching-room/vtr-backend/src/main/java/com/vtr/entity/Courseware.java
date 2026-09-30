package com.vtr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "courseware")
@EntityListeners(AuditingEntityListener.class)
public class Courseware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 10000)
    private String description;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "video_type", length = 30)
    private String videoType;

    @Column(name = "knowledge_point", length = 200)
    private String knowledgePoint;

    @Column(name = "featured")
    private Boolean featured;

    @Column(name = "audit_remark", length = 1000)
    private String auditRemark;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "version_group_id")
    private Long versionGroupId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "course_id")
    private Long courseId;

    @Column(length = 50) private String semester;
    @Column(length = 100) private String chapter;
    @Column(name = "section_id") private Long sectionId;
    @Column(length = 100) private String gradeLevel;
    @Column(length = 50) private String version;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(name = "target_audience", nullable = false, length = 20)
    private String targetAudience;

    @Column(name = "classroom_id")
    private Long classroomId;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** AI 索引状态：NOT_INDEXED、READY、METADATA_ONLY、WAITING_TRANSCRIPT、WAITING_OCR、FAILED、SUSPENDED。 */
    @Builder.Default
    @Column(name = "ai_index_status", nullable = false, length = 30)
    private String aiIndexStatus = "NOT_INDEXED";

    @Column(name = "ai_index_message", length = 1000)
    private String aiIndexMessage;

    @Column(name = "ai_document_id")
    private Long aiDocumentId;

    @Column(name = "ai_indexed_at")
    private LocalDateTime aiIndexedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
