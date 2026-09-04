package com.example.hot6novelcraft.domain.coverai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverJobCreatedEvent;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverJobResponse;
import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;
import com.example.hot6novelcraft.domain.coverai.repository.CoverJobRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoverServiceTest {

    @InjectMocks
    private CoverService coverService;

    @Mock private UserRepository userRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private CoverJobRepository coverJobRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private User author;
    private User reader;
    private Novel novel;

    @BeforeEach
    void setUp() {
        author = User.register("author@test.com", "password", "테스트작가", "01012345678", null, UserRole.AUTHOR);
        ReflectionTestUtils.setField(author, "id", 1L);

        reader = User.register("reader@test.com", "password", "테스트독자", "01098765432", null, UserRole.READER);
        ReflectionTestUtils.setField(reader, "id", 2L);

        novel = Novel.builder()
                .authorId(1L)
                .title("달빛 아래 검은 장미")
                .description("어둠의 마법사와 왕국의 기사가 금지된 사랑을 나누는 이야기")
                .genre("판타지")
                .tags("판타지,로맨스")
                .status(NovelStatus.PENDING)
                .viewCount(0L)
                .bookmarkCount(0)
                .build();
        ReflectionTestUtils.setField(novel, "id", 1L);
        ReflectionTestUtils.setField(novel, "isDeleted", false);
    }

    @Test
    @DisplayName("표지 생성 요청 성공 - jobId 즉시 반환")
    void generateCover_success() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(coverJobRepository.save(any(CoverJob.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CoverJobResponse response = coverService.generateCover(1L, 1L);

        // then
        assertThat(response.jobId()).isNotNull();
        assertThat(response.novelId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(CoverJobStatus.PENDING);
        assertThat(response.coverImageUrl()).isNull();

        verify(coverJobRepository).save(any(CoverJob.class));
        verify(eventPublisher).publishEvent(any(CoverJobCreatedEvent.class));
    }

    @Test
    @DisplayName("Kafka 발행 시 jobId와 novelId가 올바르게 전달되는지 확인")
    void generateCover_kafkaPublishWithCorrectEvent() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(coverJobRepository.save(any(CoverJob.class))).willAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<CoverJobCreatedEvent> eventCaptor = ArgumentCaptor.forClass(CoverJobCreatedEvent.class);

        // when
        CoverJobResponse response = coverService.generateCover(1L, 1L);

        // then
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CoverJobCreatedEvent event = eventCaptor.getValue();
        assertThat(event.jobId()).isEqualTo(response.jobId());
        assertThat(event.novelId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 유저 - USER_NOT_FOUND 예외")
    void generateCover_userNotFound() {
        // given
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 99L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("작가가 아닌 경우 - NOT_AUTHOR 예외")
    void generateCover_notAuthor() {
        // given
        given(userRepository.findById(2L)).willReturn(Optional.of(reader));

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 2L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("작가만 사용할 수 있습니다");
    }

    @Test
    @DisplayName("존재하지 않는 소설 - NOVEL_NOT_FOUND 예외")
    void generateCover_novelNotFound() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(novelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(99L, 1L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("소설을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 소설이 아닌 경우 - NOT_NOVEL_OWNER 예외")
    void generateCover_notNovelOwner() {
        // given
        User anotherAuthor = User.register("other@test.com", "password", "다른작가", "01011112222", null, UserRole.AUTHOR);
        ReflectionTestUtils.setField(anotherAuthor, "id", 3L);

        given(userRepository.findById(3L)).willReturn(Optional.of(anotherAuthor));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));

        // when & then
        assertThatThrownBy(() -> coverService.generateCover(1L, 3L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("본인의 소설만 표지를 생성할 수 있습니다");
    }

    @Test
    @DisplayName("상태 조회 성공")
    void getJobStatus_success() {
        // given
        CoverJob job = CoverJob.create("test-job-id", 1L, 1L);
        ReflectionTestUtils.setField(job, "status", CoverJobStatus.COMPLETED);
        ReflectionTestUtils.setField(job, "coverImageUrl", "https://s3.amazonaws.com/test.png");

        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));

        // when
        CoverJobResponse response = coverService.getJobStatus("test-job-id", 1L);

        // then
        assertThat(response.status()).isEqualTo(CoverJobStatus.COMPLETED);
        assertThat(response.coverImageUrl()).isEqualTo("https://s3.amazonaws.com/test.png");
    }

    @Test
    @DisplayName("존재하지 않는 jobId - JOB_NOT_FOUND 예외")
    void getJobStatus_jobNotFound() {
        // given
        given(coverJobRepository.findByJobId("invalid-job-id")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coverService.getJobStatus("invalid-job-id", 1L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("표지 생성 작업을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 작업이 아닌 경우 - NOT_NOVEL_OWNER 예외")
    void getJobStatus_notOwner() {
        // given
        CoverJob job = CoverJob.create("test-job-id", 1L, 1L);
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));

        // when & then
        assertThatThrownBy(() -> coverService.getJobStatus("test-job-id", 99L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("본인의 소설만 표지를 생성할 수 있습니다");
    }
}