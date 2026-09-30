package com.vtr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentRiskServiceTest {

    private final ContentRiskService service = new ContentRiskService();

    @Test
    void allowsNormalTeachingContentAndStructuredResources() {
        assertFalse(service.assess("高等数学学习交流", "请分享极限计算的不同解法").requiresManualReview());
        assertFalse(service.assess("课程知识图谱",
                "{\"nodes\":[{\"label\":\"函数\"},{\"label\":\"极限\"},{\"label\":\"导数\"}],\"links\":[{\"from\":\"函数\",\"to\":\"极限\"}]}")
                .requiresManualReview());
    }

    @Test
    void flagsHighRiskTermsAndExternalContactSpam() {
        assertTrue(service.assess("出售答案，考试前付款").requiresManualReview());
        assertTrue(service.assess("详情访问 https://example.com 并加微信咨询").requiresManualReview());
    }

    @Test
    void flagsRepeatedSpam() {
        assertTrue(service.assess("优惠优惠优惠优惠优惠优惠优惠优惠").requiresManualReview());
    }
}
