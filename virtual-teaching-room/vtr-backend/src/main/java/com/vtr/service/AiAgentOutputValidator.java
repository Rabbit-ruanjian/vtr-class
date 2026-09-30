package com.vtr.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对教研 Agent 的成品做轻量、确定性的结构检查。
 * 这不是替代教师审核，而是让明显缺章节、缺依据的结果在进入审核列表时可见。
 */
@Component
public class AiAgentOutputValidator {

    public String warning(String task, String content, boolean hasEvidence) {
        List<String> warnings = new ArrayList<>();
        if (!hasEvidence) {
            warnings.add("未检索到可用课程资料");
        }
        if (!StringUtils.hasText(content)) {
            warnings.add("AI 没有生成有效内容");
        } else {
            String normalizedTask = task == null ? "TUTOR" : task.trim().toUpperCase(Locale.ROOT);
            for (String section : requiredSections(normalizedTask)) {
                if (!content.contains(section)) {
                    warnings.add("缺少“" + section + "”部分");
                }
            }
        }
        return warnings.isEmpty() ? null : "AI 结构检查提示：" + String.join("；", warnings)
                + "。请教师审核时重点核验。";
    }

    private List<String> requiredSections(String task) {
        return switch (task) {
            case "LESSON_PLAN" -> List.of("教学目标", "重点", "难点", "教学流程");
            case "QUESTION_GENERATION" -> List.of("题目", "答案", "解析");
            case "LEARNING_ANALYSIS" -> List.of("薄弱", "建议");
            default -> List.of();
        };
    }
}
