package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 文档的文本片段，chunkIndex 从 0 开始。charEnd 为不包含在片段内的结束位置。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_document_chunk", indexes = {
        @Index(name = "idx_ai_document_chunk_document", columnList = "document_id"),
        @Index(name = "uk_ai_document_chunk_index", columnList = "document_id, chunk_index", unique = true)
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiDocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_document_chunk_document"))
    @JsonIgnore
    private AiDocument document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 入库时从 Markdown、PPT 或文档正文中提取的局部标题。 */
    @Column(name = "heading", length = 255)
    private String heading;

    /** 可选的 OpenAI 兼容 Embedding 向量，未配置向量服务时为空。 */
    @Column(name = "embedding_json", columnDefinition = "LONGTEXT")
    private String embeddingJson;

    @Column(name = "char_start", nullable = false)
    private Integer charStart;

    @Column(name = "char_end", nullable = false)
    private Integer charEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
