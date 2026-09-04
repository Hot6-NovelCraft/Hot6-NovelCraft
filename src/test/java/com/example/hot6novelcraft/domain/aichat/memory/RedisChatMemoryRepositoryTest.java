package com.example.hot6novelcraft.domain.aichat.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RKeys;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisChatMemoryRepository 테스트")
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisChatMemoryRepositoryTest {

    @InjectMocks
    private RedisChatMemoryRepository repository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RList rList;

    @Mock
    private RKeys rKeys;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final long TTL_DAYS = 7L;
    private static final String CONVERSATION_ID = "ai-support:1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repository, "keyPrefix", KEY_PREFIX);
        ReflectionTestUtils.setField(repository, "ttlDays", TTL_DAYS);
        ReflectionTestUtils.setField(repository, "objectMapper", objectMapper);
        doReturn(rList).when(redissonClient).getList(anyString());
    }

    @Nested
    @DisplayName("saveAll() - 메시지 저장")
    class SaveAllTest {

        @Test
        @DisplayName("성공 - clear → addAll → expire 순서로 실행된다")
        void saveAll_executesInCorrectOrder() {
            // given
            List<Message> messages = List.of(
                    new UserMessage("안녕하세요"),
                    new AssistantMessage("네, 안녕하세요!")
            );

            // when
            repository.saveAll(CONVERSATION_ID, messages);

            // then
            var inOrder = inOrder(rList);
            inOrder.verify(rList).clear();
            inOrder.verify(rList).addAll(anyList());
            inOrder.verify(rList).expire(any(Duration.class));
        }

        @Test
        @DisplayName("성공 - 직렬화된 메시지 개수가 원본과 동일하다")
        void saveAll_serializesCorrectCount() {
            // given
            List<Message> messages = List.of(
                    new UserMessage("질문"),
                    new AssistantMessage("답변")
            );

            // when
            repository.saveAll(CONVERSATION_ID, messages);

            // then
            verify(rList).addAll(argThat(list -> ((List<?>) list).size() == 2));
        }

        @Test
        @DisplayName("성공 - TTL이 7일로 설정된다")
        void saveAll_setsTtlTo7Days() {
            // when
            repository.saveAll(CONVERSATION_ID, List.of());

            // then
            verify(rList).expire(Duration.ofDays(7L));
        }

        @Test
        @DisplayName("성공 - Redis 키가 'keyPrefix + conversationId' 형식이다")
        void saveAll_usesCorrectRedisKey() {
            // given
            String conversationId = "ai-support:42";

            // when
            repository.saveAll(conversationId, List.of());

            // then
            verify(redissonClient).getList(KEY_PREFIX + "ai-support:42");
        }

        @Test
        @DisplayName("성공 - 빈 메시지 목록도 clear + expire 를 호출한다")
        void saveAll_emptyMessages_stillClearsAndExpires() {
            // when
            repository.saveAll(CONVERSATION_ID, List.of());

            // then
            verify(rList).clear();
            verify(rList).addAll(argThat(list -> ((List<?>) list).isEmpty()));
            verify(rList).expire(Duration.ofDays(TTL_DAYS));
        }
    }

    @Nested
    @DisplayName("findByConversationId() - 메시지 조회")
    class FindByConversationIdTest {

        @Test
        @DisplayName("성공 - USER 메시지를 역직렬화한다")
        void findByConversationId_deserializesUserMessage() {
            // given
            String json = "{\"role\":\"USER\",\"content\":\"안녕하세요\"}";
            doReturn(List.of(json)).when(rList).readAll();

            // when
            List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

            // then
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
            assertThat(messages.get(0).getText()).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("성공 - ASSISTANT 메시지를 역직렬화한다")
        void findByConversationId_deserializesAssistantMessage() {
            // given
            String json = "{\"role\":\"ASSISTANT\",\"content\":\"네, 도와드리겠습니다.\"}";
            doReturn(List.of(json)).when(rList).readAll();

            // when
            List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

            // then
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
            assertThat(messages.get(0).getText()).isEqualTo("네, 도와드리겠습니다.");
        }

        @Test
        @DisplayName("성공 - 여러 메시지를 순서대로 반환한다")
        void findByConversationId_returnsMultipleMessagesInOrder() {
            // given
            String userJson = "{\"role\":\"USER\",\"content\":\"질문\"}";
            String assistantJson = "{\"role\":\"ASSISTANT\",\"content\":\"답변\"}";
            doReturn(List.of(userJson, assistantJson)).when(rList).readAll();

            // when
            List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

            // then
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
            assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        }

        @Test
        @DisplayName("성공 - 빈 목록이면 빈 리스트를 반환한다")
        void findByConversationId_emptyList_returnsEmpty() {
            // given
            doReturn(List.of()).when(rList).readAll();

            // when
            List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

            // then
            assertThat(messages).isEmpty();
        }

        @Test
        @DisplayName("성공 - 잘못된 JSON은 필터링되어 무시된다")
        void findByConversationId_invalidJson_isFiltered() {
            // given
            String validJson = "{\"role\":\"USER\",\"content\":\"유효한 메시지\"}";
            String invalidJson = "not-valid-json";
            doReturn(List.of(validJson, invalidJson)).when(rList).readAll();

            // when
            List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

            // then
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("유효한 메시지");
        }
    }

    @Nested
    @DisplayName("deleteByConversationId() - 세션 삭제")
    class DeleteByConversationIdTest {

        @Test
        @DisplayName("성공 - 해당 conversationId의 Redis 키를 삭제한다")
        void deleteByConversationId_deletesRedisKey() {
            // when
            repository.deleteByConversationId(CONVERSATION_ID);

            // then
            verify(redissonClient).getList(KEY_PREFIX + CONVERSATION_ID);
            verify(rList).delete();
        }

        @Test
        @DisplayName("성공 - 올바른 Redis 키로 delete()를 호출한다")
        void deleteByConversationId_usesCorrectKey() {
            // given
            String conversationId = "ai-support:777";

            // when
            repository.deleteByConversationId(conversationId);

            // then
            verify(redissonClient).getList(KEY_PREFIX + "ai-support:777");
            verify(rList).delete();
        }
    }

    @Nested
    @DisplayName("findConversationIds() - 전체 대화 ID 조회")
    class FindConversationIdsTest {

        @Test
        @DisplayName("성공 - keyPrefix를 제거한 conversationId 목록을 반환한다")
        void findConversationIds_stripsKeyPrefix() {
            // given
            Iterable<String> keys = List.of(
                    KEY_PREFIX + "ai-support:1",
                    KEY_PREFIX + "ai-support:2"
            );
            doReturn(rKeys).when(redissonClient).getKeys();
            doReturn(keys).when(rKeys).getKeysByPattern(KEY_PREFIX + "*");

            // when
            List<String> ids = repository.findConversationIds();

            // then
            assertThat(ids).containsExactlyInAnyOrder("ai-support:1", "ai-support:2");
        }

        @Test
        @DisplayName("성공 - 키가 없으면 빈 리스트를 반환한다")
        void findConversationIds_noKeys_returnsEmpty() {
            // given
            doReturn(rKeys).when(redissonClient).getKeys();
            doReturn(List.of()).when(rKeys).getKeysByPattern(KEY_PREFIX + "*");

            // when
            List<String> ids = repository.findConversationIds();

            // then
            assertThat(ids).isEmpty();
        }
    }
}
