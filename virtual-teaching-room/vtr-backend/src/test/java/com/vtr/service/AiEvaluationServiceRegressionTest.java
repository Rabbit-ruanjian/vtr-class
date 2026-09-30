package com.vtr.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEvaluationServiceRegressionTest {

    @Test
    void acceptsChineseEquivalentsForEnglishCourseTerms() throws Exception {
        assertTrue(contains("操作系统通过内存和内核管理设备。", "memory"));
        assertTrue(contains("操作系统通过内存和内核管理设备。", "kernel"));
        assertTrue(contains("操作系统通过内存和内核管理设备。", "device"));
    }

    @Test
    void acceptsVirtualMemoryEvidenceFromPageTableExplanation() throws Exception {
        assertTrue(contains("page fault 发生时，CPU 检查虚拟地址在页表中的映射，并触发分页处理。", "virtual memory"));
    }

    @Test
    void doesNotAcceptUnrelatedChineseTextAsCourseTerm() throws Exception {
        assertFalse(contains("这是一个与课程无关的生活建议。", "kernel"));
    }

    private boolean contains(String answer, String keyword) throws Exception {
        AiEvaluationService service = new AiEvaluationService(null, null, null);
        Method method = AiEvaluationService.class.getDeclaredMethod(
                "containsNormalized", String.class, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, answer, keyword);
    }
}
