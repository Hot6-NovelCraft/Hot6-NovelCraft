package com.example.hot6novelcraft.domain.aichat.controller;

import com.example.hot6novelcraft.domain.aichat.dto.AiChatRequest;
import com.example.hot6novelcraft.domain.aichat.service.AiChatService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatController 테스트")
class AiChatControllerTest {

    @InjectMocks
    private AiChatController aiChatController;

    @Mock
    private AiChatService aiChatService;

    @Mock
    private UserDetailsImpl userDetails;

    @Mock
    private User user;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        given(userDetails.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
    }

    @Nested
    @DisplayName("chat() - AI 챗봇 질문")
    class ChatTest {

        @Test
        @DisplayName("성공 - 서비스에서 받은 Flux를 그대로 반환한다")
        void chat_returnsFluxFromService() {
            // given
            AiChatRequest request = new AiChatRequest("결제 방법이 궁금합니다");
            Flux<String> expected = Flux.just("카드 결제가 ", "가능합니다.");
            given(aiChatService.chat(USER_ID, request.message())).willReturn(expected);

            // when
            Flux<String> result = aiChatController.chat(userDetails, request);

            // then
            List<String> tokens = result.collectList().block();
            assertThat(tokens).containsExactly("카드 결제가 ", "가능합니다.");
        }

        @Test
        @DisplayName("성공 - userId를 userDetails에서 추출하여 서비스에 전달한다")
        void chat_extractsUserIdFromUserDetails() {
            // given
            AiChatRequest request = new AiChatRequest("안녕하세요");
            given(aiChatService.chat(USER_ID, request.message())).willReturn(Flux.just("안녕하세요!"));

            // when
            aiChatController.chat(userDetails, request).blockLast();

            // then
            verify(userDetails, times(1)).getUser();
            verify(user, times(1)).getId();
            verify(aiChatService, times(1)).chat(USER_ID, request.message());
        }

        @Test
        @DisplayName("성공 - 빈 스트림도 정상 반환된다")
        void chat_emptyFlux_returnsNormally() {
            // given
            AiChatRequest request = new AiChatRequest("질문");
            given(aiChatService.chat(USER_ID, request.message())).willReturn(Flux.empty());

            // when
            List<String> tokens = aiChatController.chat(userDetails, request).collectList().block();

            // then
            assertThat(tokens).isEmpty();
        }

        @Test
        @DisplayName("성공 - 다른 사용자는 각자의 userId로 서비스를 호출한다")
        void chat_differentUsers_callsServiceWithCorrectUserId() {
            // given
            Long anotherUserId = 99L;
            User anotherUser = mock(User.class);
            UserDetailsImpl anotherUserDetails = mock(UserDetailsImpl.class);
            given(anotherUserDetails.getUser()).willReturn(anotherUser);
            given(anotherUser.getId()).willReturn(anotherUserId);

            AiChatRequest request = new AiChatRequest("질문");
            given(aiChatService.chat(USER_ID, request.message())).willReturn(Flux.just("응답1"));
            given(aiChatService.chat(anotherUserId, request.message())).willReturn(Flux.just("응답2"));

            // when
            aiChatController.chat(userDetails, request).blockLast();
            aiChatController.chat(anotherUserDetails, request).blockLast();

            // then
            verify(aiChatService).chat(USER_ID, request.message());
            verify(aiChatService).chat(anotherUserId, request.message());
        }
    }

    @Nested
    @DisplayName("clearSession() - 대화 세션 초기화")
    class ClearSessionTest {

        @Test
        @DisplayName("성공 - 204 No Content를 반환한다")
        void clearSession_returns204() {
            // given
            doNothing().when(aiChatService).clearSession(USER_ID);

            // when
            ResponseEntity<Void> response = aiChatController.clearSession(userDetails);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("성공 - userId를 userDetails에서 추출하여 서비스에 전달한다")
        void clearSession_extractsUserIdFromUserDetails() {
            // given
            doNothing().when(aiChatService).clearSession(USER_ID);

            // when
            aiChatController.clearSession(userDetails);

            // then
            verify(userDetails, times(1)).getUser();
            verify(user, times(1)).getId();
            verify(aiChatService, times(1)).clearSession(USER_ID);
        }

        @Test
        @DisplayName("성공 - clearSession은 chat 서비스를 호출하지 않는다")
        void clearSession_doesNotCallChatService() {
            // given
            doNothing().when(aiChatService).clearSession(USER_ID);

            // when
            aiChatController.clearSession(userDetails);

            // then
            verify(aiChatService, never()).chat(any(), any());
        }
    }
}
