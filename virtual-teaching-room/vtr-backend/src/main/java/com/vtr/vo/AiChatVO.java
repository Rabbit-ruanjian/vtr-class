package com.vtr.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatVO {
    private String reply;
    private String model;
    private Long documentId;
    private Integer chunkCount;
    private List<AiSourceVO> sources;
    /** 本次回答的稳定追踪标识，用于用户反馈和问题定位。 */
    private String requestId;
    private String conversationId;
    /** DIRECT / RAG / WEB_SEARCH / RAG_AND_WEB。 */
    private String route;
    /** DIRECT / COURSE_RAG / WEB_SEARCH / RAG_AND_WEB / TEACHING_AGENT。 */
    private String answerMode;
    /** HIGH / MEDIUM / LOW / UNVERIFIED。不是“正确率”，仅表示证据可用程度。 */
    private String confidence;
    private Boolean hasEvidence;
    private Long latencyMs;
    /** NONE / KEYWORD / HYBRID_KEYWORD_TFIDF。用于观测本次 RAG 实际采用的检索策略。 */
    private String retrievalMode;
    /** 本次回答实际送入模型的课程或文档片段数量。 */
    private Integer retrievedChunkCount;
    /** 本次回答的质量门禁结果：PASS / REVIEW_REQUIRED / NO_EVIDENCE。 */
    private String qualityStatus;

    public AiChatVO(String reply, String model) {
        this(reply, model, null, null, null);
    }

    public AiChatVO(String reply, String model, Long documentId, Integer chunkCount,
                    List<AiSourceVO> sources) {
        this.reply = reply;
        this.model = model;
        this.documentId = documentId;
        this.chunkCount = chunkCount;
        this.sources = sources;
    }
}
