package com.vtr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtr.dto.AiChatRequest;
import com.vtr.repository.LearningQuestionRepository;
import com.vtr.vo.AiChatVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KimiAiServiceRegressionTest {

    @Test
    void removesLegacyFillAnswerFromQueueAndBinaryTreeConversation() throws Exception {
        String pollutedReply = "填空答案：数据元素\\\n\n"
                + "解释知识点：\n"
                + "数据结构是相互之间存在一种或多种特定关系的**数据元素**的集合。"
                + "数据元素是数据结构中的基本单位，可以是单个数据项，也可以是数据项的集合。\\\n\n"
                + "关于队列和二叉树的区别：\n队列遵循 FIFO，二叉树每个节点最多有两个子节点。";
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse(pollutedReply)),
                        ResponseEntity.ok(kimiResponse(pollutedReply)));

        AiChatVO answer = service(restTemplate).chat(request("队列和二叉树有什么区别"));

        assertFalse(answer.getReply().contains("填空答案"));
        assertFalse(answer.getReply().contains("数据结构是相互之间存在一种或多种特定关系"));
        assertTrue(answer.getReply().contains("队列"));
        assertTrue(answer.getReply().contains("二叉树"));
    }

    @Test
    void removesFilesystemExampleFromBinaryTreeAnswer() throws Exception {
        String reply = "队列是线性 FIFO 结构；二叉树是每个节点最多有两个子节点的层次结构，"
                + "常用于查找和表达式处理，如数据库索引、文件系统目录等。";
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse(reply)), ResponseEntity.ok(kimiResponse(reply)));

        AiChatVO answer = service(restTemplate).chat(request("队列和二叉树有什么区别"));

        assertFalse(answer.getReply().contains("文件系统"));
        assertTrue(answer.getReply().contains("表达式树、二叉搜索树等"));
    }

    @Test
    void keepsDataElementAnswerForDataStructureFillQuestion() throws Exception {
        String question = "填空：数据结构是相互之间存在一种或多种特定关系的______的集合。";
        String reply = "答案：数据元素\n数据结构是相互之间存在一种或多种特定关系的数据元素的集合。";
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse(reply)), ResponseEntity.ok(kimiResponse(reply)));

        AiChatVO answer = service(restTemplate).chat(request(question));

        assertTrue(answer.getReply().contains("数据元素"));
    }

    @Test
    void sendsOnlyMostRecentFourHistoryMessagesToModel() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String reply = "栈遵循后进先出原则。";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse(reply)), ResponseEntity.ok(kimiResponse(reply)));

        AiChatRequest request = request("请解释什么是栈");
        List<AiChatRequest.HistoryMessage> history = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            AiChatRequest.HistoryMessage item = new AiChatRequest.HistoryMessage();
            item.setRole(index % 2 == 0 ? "assistant" : "user");
            item.setContent("历史消息-" + index);
            history.add(item);
        }
        request.setHistory(history);

        service(restTemplate).chat(request);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) entityCaptor.getAllValues().get(0).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        String sentMessages = messages.toString();

        assertFalse(sentMessages.contains("历史消息-1"));
        assertFalse(sentMessages.contains("历史消息-2"));
        assertTrue(sentMessages.contains("历史消息-3"));
        assertTrue(sentMessages.contains("历史消息-4"));
        assertTrue(sentMessages.contains("历史消息-5"));
        assertTrue(sentMessages.contains("历史消息-6"));
    }

    @Test
    void disablesThinkingAndLimitsOutputForFastKimiConceptQuestion() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse("学习概率论可以先掌握随机变量、分布和期望。")));

        KimiAiService service = service(restTemplate);
        setField(service, "model", "kimi-k2.6");
        setField(service, "fastMode", true);

        service.chat(request("怎么学习概率论"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) entityCaptor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) payload.get("thinking");

        assertEquals("disabled", thinking.get("type"));
        assertEquals(3072, payload.get("max_completion_tokens"));
        assertFalse(payload.containsKey("max_tokens"));
    }

    @Test
    void reusesShortLivedCourseRagReplyForIdenticalQuestion() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse("根据课程资料，page table 负责地址转换。")));

        KimiAiService service = service(restTemplate);
        setField(service, "model", "kimi-k2.6");
        setField(service, "fastMode", true);

        AiChatRequest first = request("请解释 page table 的作用");
        first.setCourseId(8L);
        first.setRetrievedDocumentContext("[课程资源：xv6；片段 1] page table maps virtual address to physical address.");
        AiChatRequest second = request("请解释 page table 的作用");
        second.setCourseId(8L);
        second.setRetrievedDocumentContext(first.getRetrievedDocumentContext());

        AiChatVO firstAnswer = service.chat(first);
        AiChatVO secondAnswer = service.chat(second);

        assertEquals(firstAnswer.getReply(), secondAnswer.getReply());
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void usesPlanningAndExecutionStepsForCalculationQuestion() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("1. 识别表达式；2. 代入数值；3. 检查计算结果。")),
                        ResponseEntity.ok(kimiResponse("计算：2+3=5。最终答案：5。")),
                        ResponseEntity.ok(kimiResponse("计算正确，最终答案：5。"))
                );

        AiChatVO answer = service(restTemplate).chat(request("请计算 2+3 的结果，并写出步骤"));

        assertTrue(answer.getReply().contains("5"));
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> executionPayload = (Map<String, Object>) entityCaptor.getAllValues().get(1).getBody();
        assertTrue(executionPayload.toString().contains("内部解题计划"));
        assertTrue(executionPayload.toString().contains("后端受限计算工具已确认原题中的直接算式：2+3 = 5"));
    }

    @Test
    void asksComplexCalculationToUseNumberedSteps() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("1. 整理方程；2. 移项；3. 求解未知数。")),
                        ResponseEntity.ok(kimiResponse("1. 2x=8；2. x=4。最终答案：x=4。")),
                        ResponseEntity.ok(kimiResponse("步骤正确，最终答案：x=4。"))
                );

        service(restTemplate).chat(request("请分步解方程 2x+3=11"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> executionPayload = (Map<String, Object>) entityCaptor.getAllValues().get(1).getBody();
        assertTrue(executionPayload.toString().contains("这是复杂解题请求"));
        assertTrue(executionPayload.toString().contains("使用编号步骤"));
    }

    @Test
    void retriesTransientPlanningFailureOnlyOnce() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(ResponseEntity.ok(kimiResponse("列出已知条件和计算步骤。")))
                .thenReturn(ResponseEntity.ok(kimiResponse("计算：2+3=5。最终答案：5。")))
                .thenReturn(ResponseEntity.ok(kimiResponse("计算正确，最终答案：5。")));

        AiChatVO answer = service(restTemplate).chat(request("请计算 2+3 的结果，并写出步骤"));

        assertTrue(answer.getReply().contains("5"));
        verify(restTemplate, times(4)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void returnsVerifiedResultDirectlyForSimpleArithmeticWithEscapedMultiplication() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);

        AiChatVO answer = service(restTemplate).chat(request("请计算 (2+3) \\\\\\*1.5 的结果"));

        assertEquals("计算：(2+3)*1.5 = 7.5。", answer.getReply());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void verifiesPureArithmeticStatementWithoutCallingModel() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);

        AiChatVO correct = service(restTemplate).chat(request("(2+3) \\\\\\*1.5 = 7.5"));
        AiChatVO incorrect = service(restTemplate).chat(request("(2+3)*1.5 = 8"));

        assertEquals("等式正确：(2+3)*1.5 = 7.5。", correct.getReply());
        assertEquals("等式不正确：(2+3)*1.5 的计算结果是 7.5，不是 8。", incorrect.getReply());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void removesBareLegacyDataElementAnswerBeforeReview() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse("数据元素")),
                        ResponseEntity.ok(kimiResponse("栈遵循后进先出原则。")));

        AiChatVO answer = service(restTemplate).chat(request("请解释什么是栈"));

        assertFalse(answer.getReply().equals("数据元素"));
        assertTrue(answer.getReply().contains("后进先出"));
    }

    @Test
    void removesLegacyFillQuestionAnswerVariantFromEquationSolution() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(kimiResponse(
                                "1. 识别变量等式；2. 移项；3. 求出 x。")),
                        ResponseEntity.ok(kimiResponse("解得 x=4。\\n\\n填空题答案：数据元素")),
                        ResponseEntity.ok(kimiResponse("2x+3=11。两边减去3得 2x=8；"
                                + "等式两边同时除以 2（x 的系数），得 x=4。")));

        AiChatVO answer = service(restTemplate).chat(request("2x+3=11解除X"));

        assertFalse(answer.getReply().contains("填空题答案"));
        assertFalse(answer.getReply().contains("数据元素"));
        assertTrue(answer.getReply().contains("x=4"));
        assertTrue(answer.getReply().contains("等式两边同时除以 2（x 的系数）"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> executionPayload = (Map<String, Object>) entityCaptor.getAllValues().get(1).getBody();
        assertTrue(executionPayload.toString().contains("变量等式"));
        assertTrue(executionPayload.toString().contains("解除X"));
        assertTrue(executionPayload.toString().contains("等式两边同时除以 2（x 的系数）"));
    }

    @Test
    void recognizesCommonEquationWordingVariantsWithoutHistoryPollution() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("计划：整理方程并求出 x。")),
                        ResponseEntity.ok(kimiResponse("由 2x+3=11 得 x=4。")),
                        ResponseEntity.ok(kimiResponse("最终答案：x=4。")),
                        ResponseEntity.ok(kimiResponse("计划：整理方程并求出 x。")),
                        ResponseEntity.ok(kimiResponse("由 2x+3=11 得 x=4。")),
                        ResponseEntity.ok(kimiResponse("最终答案：x=4。")),
                        ResponseEntity.ok(kimiResponse("计划：整理方程并求出 x。")),
                        ResponseEntity.ok(kimiResponse("由 2x+3=11 得 x=4。")),
                        ResponseEntity.ok(kimiResponse("最终答案：x=4。")));

        AiChatRequest requestWithHistory = request("2x+3=11解出X");
        AiChatRequest.HistoryMessage oldAnswer = new AiChatRequest.HistoryMessage();
        oldAnswer.setRole("assistant");
        oldAnswer.setContent("答案：数据元素");
        requestWithHistory.setHistory(List.of(oldAnswer));

        AiChatVO first = service(restTemplate).chat(requestWithHistory);
        AiChatVO second = service(restTemplate).chat(request("2x+3=11求x"));
        AiChatVO third = service(restTemplate).chat(request("2x+3=11解 x"));

        assertTrue(first.getReply().contains("x=4"));
        assertTrue(second.getReply().contains("x=4"));
        assertTrue(third.getReply().contains("x=4"));
        assertFalse(first.getReply().contains("数据元素"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(9)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        for (HttpEntity<?> entity : entityCaptor.getAllValues()) {
            assertTrue(entity.getBody().toString().contains("变量等式"));
        }
    }

    @Test
    void treatsBareCompleteEquationAsCalculationRequest() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("计划：移项并求出 x。")),
                        ResponseEntity.ok(kimiResponse("2x+3=11，减去3得 2x=8，再除以2得 x=4。")),
                        ResponseEntity.ok(kimiResponse("最终结果：x=4。")));

        AiChatVO answer = service(restTemplate).chat(request("2x+3=11"));

        assertTrue(answer.getReply().contains("x=4"));
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotGuessWhenVariableEquationIsIncomplete() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);

        AiChatVO answer = service(restTemplate).chat(request("x=？"));
        AiChatVO anotherAnswer = service(restTemplate).chat(request("2x+3=?"));

        assertEquals("题目信息不足：只有 x=？，无法确定 x。请提供完整方程。", answer.getReply());
        assertEquals("题目信息不足：只有 2x+3=？，无法确定 x。请提供完整方程。", anotherAnswer.getReply());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void answersMultipleEquationsIndependentlyInOneMessage() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("计划：移项并求出 x。")),
                        ResponseEntity.ok(kimiResponse("2x+3=11，减去3得 2x=8，再除以2得 x=4。")),
                        ResponseEntity.ok(kimiResponse("最终结果：x=4。")));

        AiChatVO answer = service(restTemplate).chat(request("2x+3=11 x=？ 2x+3=?"));

        assertTrue(answer.getReply().contains("第1题（2x+3=11）"));
        assertTrue(answer.getReply().contains("x=4"));
        assertTrue(answer.getReply().contains("第2题（x=？）"));
        assertTrue(answer.getReply().contains("只有 x=？，无法确定 x"));
        assertTrue(answer.getReply().contains("第3题（2x+3=?）"));
        assertTrue(answer.getReply().contains("只有 2x+3=？，无法确定 x"));
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void separatesIndependentSingleVariableEquationsWithDifferentVariables() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("计划：整理 x=1 并确认 x。")),
                        ResponseEntity.ok(kimiResponse("由 x=1 可得 x=1。")),
                        ResponseEntity.ok(kimiResponse("最终答案：x=1。")),
                        ResponseEntity.ok(kimiResponse("计划：整理 y=2 并确认 y。")),
                        ResponseEntity.ok(kimiResponse("由 y=2 可得 y=2。")),
                        ResponseEntity.ok(kimiResponse("最终答案：y=2。")));

        AiChatVO answer = service(restTemplate).chat(request("x=1 y=2"));

        assertTrue(answer.getReply().contains("第1题（x=1）"));
        assertTrue(answer.getReply().contains("第2题（y=2）"));
        assertTrue(answer.getReply().contains("x=1"));
        assertTrue(answer.getReply().contains("y=2"));
        verify(restTemplate, times(6)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void keepsTwoVariableEquationSystemTogether() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("计划：联立两个方程求解。")),
                        ResponseEntity.ok(kimiResponse("由 x+y=3、x-y=1 联立可得 x=2，y=1。")),
                        ResponseEntity.ok(kimiResponse("最终答案：x=2，y=1。")));

        AiChatVO answer = service(restTemplate).chat(request("x+y=3 x-y=1"));

        assertFalse(answer.getReply().contains("第1题（"));
        assertTrue(answer.getReply().contains("x=2"));
        assertTrue(answer.getReply().contains("y=1"));
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotSplitPureArithmeticStatements() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("这是一条包含两个算式的普通问题。")),
                        ResponseEntity.ok(kimiResponse("请分别说明两个算式的结果。")));

        AiChatVO answer = service(restTemplate).chat(request("2+3=5 4+1=5"));

        assertFalse(answer.getReply().contains("第1题（"));
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotSplitDocumentImageOrQuestionBoundRequests() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));

        AiChatRequest documentRequest = request("2x+3=11 x=1");
        documentRequest.setDocumentText("文档正文");

        AiChatRequest imageRequest = request("2x+3=11 x=1");
        imageRequest.setImageData("data:image/png;base64,AAAA");

        AiChatRequest questionRequest = request("2x+3=11 x=1");
        questionRequest.setQuestionId(12L);

        assertTrue(splitCompoundRequest(service, documentRequest).isEmpty());
        assertTrue(splitCompoundRequest(service, imageRequest).isEmpty());
        assertTrue(splitCompoundRequest(service, questionRequest).isEmpty());
    }

    @Test
    void doesNotForceCourseRagForLifeQuestionsOrSimpleArithmetic() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));

        AiChatRequest lifeQuestion = request("最近压力很大怎么办");
        lifeQuestion.setCourseId(9L);
        AiChatRequest arithmeticQuestion = request("1+1");
        arithmeticQuestion.setCourseId(9L);

        assertEquals("DIRECT", service.routeName(lifeQuestion));
        assertEquals("DIRECT", service.routeName(arithmeticQuestion));
    }

    @Test
    void keepsCourseKnowledgeForQuestionContextOnCoursePage() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));

        AiChatRequest courseQuestion = request("解释这道题");
        courseQuestion.setCourseId(9L);
        courseQuestion.setContextExcerpt("死锁产生的四个必要条件是什么？");

        assertEquals("RAG", service.routeName(courseQuestion));
        assertTrue(service.shouldUseCourseKnowledge(courseQuestion));
    }

    @Test
    void doesNotTreatTechnicalNotificationAsExternalSearchOnCoursePage() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));
        AiChatRequest courseQuestion = request(
                "请根据课程资料解释设备事件如何通知内核，以及 interrupt 和 trap 的区别");
        courseQuestion.setCourseId(8L);

        assertEquals("RAG", service.routeName(courseQuestion));
    }

    @Test
    void routesAmbiguousTitlePhraseToWebSearchEvenFromCoursePage() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));
        AiChatRequest request = request("咸的玩笑");
        request.setCourseId(9L);

        assertFalse(service.shouldUseCourseKnowledge(request));
        assertEquals("WEB_SEARCH", service.routeName(request));
    }

    @Test
    void routesQuotedTitleQuestionToWebSearch() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));

        assertEquals("WEB_SEARCH", service.routeName(request("《咸的玩笑》是什么")));
    }

    @Test
    void doesNotTreatCourseOrLifePhrasesAsAmbiguousTitles() throws Exception {
        KimiAiService service = service(mock(RestTemplate.class));

        AiChatRequest courseQuestion = request("数据结构的定义");
        courseQuestion.setCourseId(9L);
        AiChatRequest lifeQuestion = request("生活中的焦虑");
        lifeQuestion.setCourseId(9L);

        assertEquals("RAG", service.routeName(courseQuestion));
        assertEquals("DIRECT", service.routeName(lifeQuestion));
    }

    @Test
    void usesSingleVerifiedAnswerForAmbiguousTitleInFastMode() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiWebSearchToolCall("咸的玩笑 刘震云 小说")),
                        ResponseEntity.ok(kimiResponse(
                                "“咸的玩笑”既可按日常谐音理解，也指刘震云的长篇小说《咸的玩笑》。你想了解哪一种？"))
                );

        KimiAiService service = service(restTemplate);
        setField(service, "model", "kimi-k2.6");
        setField(service, "fastMode", true);

        AiChatVO answer = service.chat(request("咸的玩笑"));

        assertEquals("WEB_SEARCH", answer.getRoute());
        assertTrue(answer.getReply().contains("刘震云"));
        assertTrue(answer.getReply().contains("小说"));
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstPayload = (Map<String, Object>) entityCaptor.getAllValues().get(0).getBody();
        assertTrue(firstPayload.containsKey("tools"));
        assertTrue(firstPayload.toString().contains("可能存在多重含义"));
        assertTrue(firstPayload.toString().contains("书名、小说、电影"));
        assertTrue(firstPayload.toString().contains("不要主动写‘与课程无关’"));
    }

    @Test
    void retriesAmbiguousTitleWhenModelAnswersWithoutCallingSearch() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(kimiResponse("这可能是一个网络用语。")),
                        ResponseEntity.ok(kimiWebSearchToolCall("咸的玩笑 书名 作者 小说")),
                        ResponseEntity.ok(kimiResponse(
                                "《咸的玩笑》是刘震云创作的长篇小说，也可能存在其他语境。"))
                );

        KimiAiService service = service(restTemplate);
        setField(service, "model", "kimi-k2.6");
        setField(service, "fastMode", true);

        AiChatVO answer = service.chat(request("咸的玩笑"));

        assertTrue(answer.getReply().contains("刘震云"));
        assertTrue(answer.getReply().contains("长篇小说"));
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> retryPayload = (Map<String, Object>) entityCaptor.getAllValues().get(1).getBody();
        assertTrue(retryPayload.toString().contains("必须先调用 $web_search"));
    }

    @Test
    void fallsBackToCautiousDirectAnswerWhenOptionalSearchIsUnavailable() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        HttpClientErrorException unsupportedSearch = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"error\":{\"message\":\"Permission denied for $web_search tool\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(unsupportedSearch)
                .thenReturn(ResponseEntity.ok(kimiResponse(
                        "这个短语可能有多种含义：它可能是作品名，也可能是日常表达。你指的是哪一种？")));

        KimiAiService service = service(restTemplate);
        setField(service, "model", "kimi-k2.6");
        setField(service, "fastMode", true);

        AiChatVO answer = service.chat(request("咸的玩笑"));

        assertEquals("DIRECT", answer.getRoute());
        assertTrue(answer.getReply().contains("多种含义"));
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    private KimiAiService service(RestTemplate restTemplate) throws Exception {
        KimiAiService service = new KimiAiService(
                restTemplate,
                new ObjectMapper(),
                mock(LearningQuestionRepository.class)
        );
        setField(service, "apiKey", "regression-test-key");
        setField(service, "baseUrl", "https://example.invalid/v1");
        setField(service, "model", "test-model");
        return service;
    }

    private AiChatRequest request(String message) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        return request;
    }

    private String kimiResponse(String reply) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + new ObjectMapper().valueToTree(reply).toString()
                + "},\"finish_reason\":\"stop\"}]}";
    }

    private String kimiWebSearchToolCall(String query) {
        String arguments = new ObjectMapper().valueToTree(Map.of("query", query)).toString();
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{"
                + "\"id\":\"call_search_1\",\"type\":\"builtin_function\",\"function\":{"
                + "\"name\":\"$web_search\",\"arguments\":"
                + new ObjectMapper().valueToTree(arguments).toString()
                + "}}]},\"finish_reason\":\"tool_calls\"}]}";
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private List<String> splitCompoundRequest(KimiAiService service, AiChatRequest request) throws Exception {
        Method method = KimiAiService.class.getDeclaredMethod("splitCompoundRequest", AiChatRequest.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, request);
    }
}
