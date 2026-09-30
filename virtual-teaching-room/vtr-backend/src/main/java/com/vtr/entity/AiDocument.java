package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * AI 助手上传文档的持久化记录。
 *
 * <p>当前阶段只保存提取后的文本，后续接入向量库时可以直接使用对应的片段记录。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_document", indexes = {
        @Index(name = "idx_ai_document_owner", columnList = "owner_id"),
        @Index(name = "idx_ai_document_owner_status", columnList = "owner_id, status"),
        @Index(name = "idx_ai_document_courseware", columnList = "courseware_id, is_deleted")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文档所属用户，始终由服务端从当前登录会话取得。 */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 课程级知识库文档；个人上传文档时可以为空。 */
    @Column(name = "course_id")
    private Long courseId;

    /** 普通课程课件对应的索引文档；个人上传或独立课程资料可以为空。 */
    @Column(name = "courseware_id")
    private Long coursewareId;

    @Column(name = "chapter", length = 100)
    private String chapter;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "license", length = 100)
    private String license;

    @Column(name = "source_author", length = 255)
    private String sourceAuthor;

    @Column(name = "attribution", length = 1000)
    private String attribution;

    /** 原始文件保存在本系统 uploads 目录中的访问路径。 */
    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "file_extension", length = 20)
    private String fileExtension;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(name = "text_length", nullable = false)
    private Integer textLength;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(nullable = false, length = 20)
    private String status = "PENDING_REVIEW";

    /** 课程资源审核意见；个人文档通常为空。 */
    @Column(name = "review_remark", length = 1000)
    private String reviewRemark;

    /** 审核人和审核时间由服务端写入。 */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 建索引时采用的内容来源：TEXT_READY / METADATA_ONLY。 */
    @Column(name = "index_mode", length = 30)
    private String indexMode;
}
