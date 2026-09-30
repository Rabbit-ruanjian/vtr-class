package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vtr.common.BaseEntity;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "code_snippet", indexes = {
        @Index(name = "idx_language", columnList = "language"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_author", columnList = "author_id"),
        @Index(name = "idx_created", columnList = "created_at")
})
@Where(clause = "is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CodeSnippet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String code;

    @NotBlank
    @Column(nullable = false, length = 30)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    @JsonIgnoreProperties({"password", "snippets", "submissions"})
    private User author;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "like_count")
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "download_count")
    @Builder.Default
    private Integer downloadCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SnippetStatus status = SnippetStatus.PENDING;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "snippet_tags",
            joinColumns = @JoinColumn(name = "snippet_id"),
            indexes = @Index(name = "idx_tag", columnList = "tag")
    )
    @Column(name = "tag", length = 30)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "review_remark", columnDefinition = "TEXT")
    private String reviewRemark;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ========== 时间字段（关键修复） ==========
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SnippetStatus {
        PENDING("待审核"),
        APPROVED("已通过"),
        REJECTED("已拒绝");

        private final String description;

        SnippetStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // ========== 业务方法 ==========
    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementDownloadCount() {
        this.downloadCount++;
    }

    public void approve(Long reviewerId, String remark) {
        this.status = SnippetStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewRemark = remark;
    }

    public void reject(Long reviewerId, String remark) {
        this.status = SnippetStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewRemark = remark;
    }

    @Override
    public boolean isEnabled() {
        if (isDeleted != null && isDeleted) {
            return false;
        }
        if (status == null) {
            return false;
        }
        return status == SnippetStatus.APPROVED;
    }

    public boolean isPublic() {
        return isPublic != null && isPublic;
    }

    public boolean isPending() {
        return status == SnippetStatus.PENDING;
    }

    public boolean isApproved() {
        return status == SnippetStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == SnippetStatus.REJECTED;
    }

    public boolean canBeViewedBy(User user) {
        if (isPublic() && isEnabled()) {
            return true;
        }
        return author != null && user != null && author.getId().equals(user.getId());
    }
}