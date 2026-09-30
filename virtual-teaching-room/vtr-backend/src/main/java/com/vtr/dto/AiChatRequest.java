package com.vtr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class AiChatRequest {

    /** 由服务端生成，仅用于回答追踪和反馈关联，客户端提交值会被忽略。 */
    @JsonIgnore
    private String requestId;

    /** 由前端为每个对话窗口生成，新建对话时才变化，用于把同一窗口的问答聚合成一条侧边栏记录。 */
    @Size(max = 64, message = "对话 ID 不能超过 64 个字符")
    private String conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息内容不能超过 8000 个字符")
    private String message;

    @Size(max = 200, message = "上下文标题不能超过 200 个字符")
    private String contextTitle;

    @Size(max = 200, message = "上下文信息不能超过 200 个字符")
    private String contextMeta;

    /** 页面已有题型时由前端传入，服务端只按白名单识别，不把它当作提示词执行。 */
    @Size(max = 30, message = "题型信息不能超过 30 个字符")
    private String questionType;

    /** 当前题库题目的 ID，仅用于服务端做答案一致性校验，不会传给 Kimi。 */
    @Positive(message = "题目 ID 必须为正数")
    private Long questionId;

    @Size(max = 12000, message = "题目上下文不能超过 12000 个字符")
    private String contextExcerpt;

    @Size(max = 30000, message = "文档内容不能超过 30000 个字符")
    private String documentText;

    @Size(max = 255, message = "文档名称不能超过 255 个字符")
    private String documentName;

    /** 后续追问所关联的文档 ID，由服务端校验当前用户归属。 */
    @Positive(message = "文档 ID 必须为正数")
    private Long documentId;

    /** 当前课程，用于课程级本地 RAG。 */
    @Positive(message = "课程 ID 必须为正数")
    private Long courseId;

    @Size(max = 100, message = "章节不能超过 100 个字符")
    private String chapter;

    /** 由 Agent 入口设置的任务类型，不接受任意系统提示词。 */
    @Size(max = 40, message = "AI 任务类型不能超过 40 个字符")
    private String agentTask;

    /** 服务端根据 documentId 检索出的片段，不接受客户端直接作为知识库内容。 */
    @Size(max = 12000, message = "检索到的文档片段不能超过 12000 个字符")
    private String retrievedDocumentContext;

    @Size(max = 12000000, message = "图片数据过大，请压缩后重试")
    private String imageData;

    /** 多模态视频数据，通常由服务端上传接口转换为 data:video/*;base64。 */
    @Size(max = 60000000, message = "视频数据过大，请压缩后重试")
    private String videoData;

    @Size(max = 10, message = "单次最多携带 10 条历史消息")
    @Valid
    private List<HistoryMessage> history;

    @Data
    public static class HistoryMessage {

        @NotBlank(message = "历史消息角色不能为空")
        private String role;

        @NotBlank(message = "历史消息内容不能为空")
        @Size(max = 8000, message = "历史消息不能超过 8000 个字符")
        private String content;
    }
}
