package com.vtr.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 回答所使用的文档片段依据。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiSourceVO {
    /** 面向用户展示的片段编号，从 1 开始。 */
    private Integer chunkIndex;
    private Integer charStart;
    private Integer charEnd;
    private String excerpt;
    private Long documentId;
    private String documentName;
    private String chapter;
    /** 从文档标题或章节标题中提取的局部标题，帮助用户理解引用位置。 */
    private String heading;
    /** 课程资源的来源元数据；个人上传文档可以为空。 */
    private String sourceType;
    private String sourceUrl;
    private String license;
    private String sourceAuthor;
    private String attribution;
    /** COURSE / DOCUMENT / WEB。前端据此显示本地资料或联网来源。 */
    private String sourceKind;

    public AiSourceVO(Integer chunkIndex, Integer charStart, Integer charEnd, String excerpt) {
        this.chunkIndex = chunkIndex;
        this.charStart = charStart;
        this.charEnd = charEnd;
        this.excerpt = excerpt;
    }

    public AiSourceVO(Integer chunkIndex, Integer charStart, Integer charEnd, String excerpt,
                      Long documentId, String documentName, String chapter) {
        this(chunkIndex, charStart, charEnd, excerpt);
        this.documentId = documentId;
        this.documentName = documentName;
        this.chapter = chapter;
    }
}
