package com.example.hot6novelcraft.domain.aichat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatService 테스트")
class AiChatServiceTest {

    @InjectMocks
    private AiChatService aiChatService;

    @Mock
    private ChatClient customerServiceChatClient;

    @Mock
    private ChatMemory chatMemory;

    private void mockRequestChain(ChatClientRequestSpec requestSpec,
                                  StreamResponseSpec streamSpec,
                                  String message,
                                  Flux<String> responseFlux) {
        given(customerServiceChatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(message)).willReturn(requestSpec);
        given(requestSpec.advisors(any(MessageChatMemoryAdvisor.class))).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamSpec);
        given(streamSpec.content()).willReturn(responseFlux);
    }

    @Nested
    @DisplayName("chat() - AI 응답 스트리밍")
    class ChatTest {

        @Test
        @DisplayName("성공 - AI 응답 토큰들이 Flux로 스트리밍된다")
        void chat_streamsTokens() {
            // given
            Long userId = 1L;
            String message = "결제 방법이 궁금합니다";
            Flux<String> responseFlux = Flux.just("카드 ", "결제가 ", "가능합니다.");

            ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
            StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);
            mockRequestChain(requestSpec, streamSpec, message, responseFlux);

            // when
            List<String> tokens = aiChatService.chat(userId, message).collectList().block();

            // then
            assertThat(tokens).containsExactly("카드 ", "결제가 ", "가능합니다.");
        }

        @Test
        @DisplayName("성공 - 단일 토큰 응답도 정상 처리된다")
        void chat_singleToken_returnsCorrectly() {
            // given
            Long userId = 42L;
            String message = "안녕하세요";
            Flux<String> responseFlux = Flux.just("안녕하세요!");

            ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
            StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);
            mockRequestChain(requestSpec, streamSpec, message, responseFlux);

            // when
            List<String> tokens = aiChatService.chat(userId, message).collectList().block();

            // then
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0)).isEqualTo("안녕하세요!");
        }

        @Test
        @DisplayName("성공 - ChatClient 체이닝 순서가 올바르다 (prompt→user→advisors→stream→content)")
        void chat_callsChainInCorrectOrder() {
            // given
            Long userId = 1L;
            String message = "포인트 충전 방법은요?";
            Flux<String> responseFlux = Flux.just("마이페이지에서 충전하세요.");

            ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
            StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);
            mockRequestChain(requestSpec, streamSpec, message, responseFlux);

            // when
            aiChatService.chat(userId, message).blockLast();

            // then
            var inOrder = inOrder(customerServiceChatClient, requestSpec, streamSpec);
            inOrder.verify(customerServiceChatClient).prompt();
            inOrder.verify(requestSpec).user(message);
            inOrder.verify(requestSpec).advisors(any(MessageChatMemoryAdvisor.class));
            inOrder.verify(requestSpec).stream();
            inOrder.verify(streamSpec).content();
        }

        @Test
        @DisplayName("성공 - 빈 응답 Flux도 정상 완료된다")
        void chat_emptyResponse_completesNormally() {
            // given
            Long userId = 1L;
            String message = "질문";

            ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
            StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);
            mockRequestChain(requestSpec, streamSpec, message, Flux.empty());

            // when
            List<String> tokens = aiChatService.chat(userId, message).collectList().block();

            // then
            assertThat(tokens).isEmpty();
        }

        @Test
        @DisplayName("성공 - prompt()는 매 요청마다 한 번씩만 호출된다")
        void chat_promptCalledOncePerRequest() {
            // given
            Long userId = 1L;
            String message = "질문";
            ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
            StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);
            mockRequestChain(requestSpec, streamSpec, message, Flux.just("응답"));

            // when
            aiChatService.chat(userId, message).blockLast();

            // then
            verify(customerServiceChatClient, times(1)).prompt();
        }
    }

    @Nested
    @DisplayName("clearSession() - 대화 세션 초기화")
    class ClearSessionTest {

        @Test
        @DisplayName("성공 - 'ai-support:{userId}' conversationId로 chatMemory.clear() 호출")
        void clearSession_callsChatMemoryWithCorrectId() {
            // when
            aiChatService.clearSession(7L);

            // then
            verify(chatMemory, times(1)).clear("ai-support:7");
        }

        @Test
        @DisplayName("성공 - 다른 userId는 각각의 conversationId로 초기화된다")
        void clearSession_differentUsers_clearDifferentSessions() {
            // when
            aiChatService.clearSession(1L);
            aiChatService.clearSession(99L);

            // then
            verify(chatMemory).clear("ai-support:1");
            verify(chatMemory).clear("ai-support:99");
            verifyNoMoreInteractions(chatMemory);
        }

        @Test
        @DisplayName("성공 - clearSession은 ChatClient를 호출하지 않는다")
        void clearSession_doesNotTouchChatClient() {
            // when
            aiChatService.clearSession(1L);

            // then
            verifyNoInteractions(customerServiceChatClient);
        }
    }
}
