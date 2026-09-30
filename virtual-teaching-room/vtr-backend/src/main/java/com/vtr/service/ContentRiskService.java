package com.vtr.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ContentRiskService {

    private static final List<String> HIGH_RISK_TERMS = List.of(
            "代考", "出售答案", "购买答案", "兼职刷单", "返利诈骗", "银行卡转账",
            "裸聊", "色情服务", "枪支弹药", "制作炸弹", "自杀教程", "威胁杀害"
    );
    private static final Pattern EXTERNAL_LINK = Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT_SPAM = Pattern.compile("(加|联系).{0,6}(微信|vx|qq|群)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED_TEXT = Pattern.compile("(.{1,20})\\1{5,}", Pattern.DOTALL);

    public RiskAssessment assess(String... values) {
        String content = String.join(" ", java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .toArray(String[]::new)).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(content)) return RiskAssessment.safe();

        for (String term : HIGH_RISK_TERMS) {
            if (content.contains(term)) {
                return new RiskAssessment("HIGH", "命中高风险词：" + term, true);
            }
        }
        if (EXTERNAL_LINK.matcher(content).find() && CONTACT_SPAM.matcher(content).find()) {
            return new RiskAssessment("HIGH", "疑似外链引流或广告信息", true);
        }
        String compactContent = content.replaceAll("\\s+", "");
        if (compactContent.length() > 12 && REPEATED_TEXT.matcher(compactContent).find()) {
            return new RiskAssessment("HIGH", "疑似重复刷屏内容", true);
        }
        return RiskAssessment.safe();
    }

    public record RiskAssessment(String level, String reason, boolean requiresManualReview) {
        public static RiskAssessment safe() {
            return new RiskAssessment("LOW", "未发现需要人工复核的风险", false);
        }
    }
}
