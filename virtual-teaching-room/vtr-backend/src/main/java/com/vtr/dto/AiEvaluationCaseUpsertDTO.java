package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 创建课程 AI 评测样本时使用的白名单字段。 */
@Data
public class AiEvaluationCaseUpsertDTO {
    @NotBlank(message = "评测标题不能为空")
    @Size(max = 255, message = "评测标题不能超过 255 个字符")
    private String title;

    @NotBlank(message = "评测问题不能为空")
    @Size(max = 8000, message = "评测问题不能超过 8000 个字符")
    private String question;

    @Size(max = 100, message = "章节不能超过 100 个字符")
    private String chapter;

    @Size(max = 4000, message = "必答要点不能超过 4000 个字符")
    private String requiredKeywords;

    @Size(max = 4000, message = "禁止词不能超过 4000 个字符")
    private String forbiddenKeywords;

    @Size(max = 40, message = "期望路由不能超过 40 个字符")
    private String expectedRoute;

    private Boolean expectedEvidence = true;
    private Boolean enabled = true;
}
