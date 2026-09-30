package com.vtr.vo;

import com.vtr.entity.AiConversation;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 侧边栏“最近对话”按会话聚合后的一条记录：一个对话窗口对应一条，
 * 内部保留该会话的全部问答轮次，供点击后完整恢复。
 */
@Getter
@Setter
public class AiConversationSummary {

    /**
     * 侧边栏稳定标识：新数据为真实 conversationId；旧数据（无 conversationId）
     * 合成为 legacy-row-{id}。前端用它做列表 key、高亮与删除，避免把合成 key 当作真实会话 ID 回写。
     */
    private String key;

    /** 真实会话 ID；旧数据为 null。前端只在非空时才作为“继续同一对话”的上下文回写。 */
    private String conversationId;

    /** 代表性行 ID（该会话最新一轮），兼容旧的按行高亮逻辑。 */
    private Long id;

    /** 会话标题：取第一条用户提问。 */
    private String title;

    /** 首个问题原文，供搜索与展示。 */
    private String question;

    /** 最近一轮回答，用于列表副标题预览。 */
    private String answer;

    /** 会话内最近使用到的文档 ID，点击恢复时沿用。 */
    private Long documentId;

    /** 会话最近活跃时间，用于排序与展示。 */
    private LocalDateTime createdAt;

    /** 该会话包含的问答轮数。 */
    private int turnCount;

    /** 该会话的全部问答轮次（按时间正序），点击后完整恢复到窗口。 */
    private List<AiConversation> turns;
}
