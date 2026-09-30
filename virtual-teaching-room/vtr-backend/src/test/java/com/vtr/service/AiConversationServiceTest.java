package com.vtr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtr.dto.AiChatRequest;
import com.vtr.entity.AiConversation;
import com.vtr.repository.AiConversationRepository;
import com.vtr.repository.AiFeedbackRepository;
import com.vtr.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationServiceTest {

    private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
    private final AiFeedbackRepository feedbackRepository = mock(AiFeedbackRepository.class);
    private final AiConversationService service = new AiConversationService(
            conversationRepository, feedbackRepository, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresSavedHistoryInChronologicalOrderWithoutDuplicatingClientSuffix() {
        loginAs(42L);
        AiConversation latest = conversation("第二个问题", "第二个回答");
        AiConversation earlier = conversation("第一个问题", "第一个回答");
        when(conversationRepository.findTop30ByUserIdOrderByCreatedAtDescIdDesc(42L))
                .thenReturn(List.of(latest, earlier));

        AiChatRequest request = new AiChatRequest();
        request.setHistory(new ArrayList<>(List.of(
                history("user", "第二个问题"), history("assistant", "第二个回答"))));

        service.restoreHistory(request);

        assertEquals(List.of("第一个问题", "第一个回答", "第二个问题", "第二个回答"),
                request.getHistory().stream().map(AiChatRequest.HistoryMessage::getContent).toList());
        verify(conversationRepository).findTop30ByUserIdOrderByCreatedAtDescIdDesc(42L);
    }

    @Test
    void clearsOnlyTheAuthenticatedUsersMemory() {
        loginAs(42L);

        service.clearForCurrentUser();

        InOrder deletionOrder = inOrder(feedbackRepository, conversationRepository);
        deletionOrder.verify(feedbackRepository).deleteByUserId(42L);
        deletionOrder.verify(conversationRepository).deleteByUserId(42L);
    }

    private void loginAs(Long userId) {
        CustomUserDetails principal = new CustomUserDetails(userId, "memory-test", "password",
                null, false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AiConversation conversation(String question, String answer) {
        AiConversation conversation = new AiConversation();
        conversation.setQuestion(question);
        conversation.setAnswer(answer);
        return conversation;
    }

    private AiChatRequest.HistoryMessage history(String role, String content) {
        AiChatRequest.HistoryMessage message = new AiChatRequest.HistoryMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
