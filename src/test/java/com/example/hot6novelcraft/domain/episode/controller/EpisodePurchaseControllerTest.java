package com.example.hot6novelcraft.domain.episode.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.point.service.EpisodePurchaseFacade;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EpisodePurchaseController 테스트")
class EpisodePurchaseControllerTest {

    @InjectMocks
    private EpisodePurchaseController episodePurchaseController;

    @Mock
    private EpisodePurchaseFacade purchaseFacade;

    private MockMvc mockMvc;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 100L;
    private static final Long NOVEL_ID = 10L;
    private static final int EPISODE_PRICE = 200;
    private static final Long REMAINING_BALANCE = 9800L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(episodePurchaseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver()
                )
                .build();

        // Mock User 생성
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(USER_ID);
        given(mockUser.getRole()).willReturn(UserRole.READER);
        given(mockUser.getPassword()).willReturn("password");
        given(mockUser.getEmail()).willReturn("test@test.com");

        // UserDetailsImpl 실제 객체 생성
        UserDetailsImpl userDetails = new UserDetailsImpl(mockUser, null);

        // SecurityContext에 인증 정보 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // 단건 회차 구매 테스트
    // =========================================================
    @Nested
    @DisplayName("POST /api/episodes/{episodeId}/purchase - 회차 단건 구매")
    class PurchaseEpisode {

        @Test
        @DisplayName("성공 - 회차 구매 성공")
        void purchaseEpisode_success() throws Exception {
            // given
            EpisodePurchaseResponse mockResponse = new EpisodePurchaseResponse(
                    EPISODE_ID, NOVEL_ID,"테스트 회차", EPISODE_PRICE, REMAINING_BALANCE
            );
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("회차 구매 성공"))
                    .andExpect(jsonPath("$.data.episodeId").value(EPISODE_ID))
                    .andExpect(jsonPath("$.data.episodeTitle").value("테스트 회차"))
                    .andExpect(jsonPath("$.data.pointPrice").value(EPISODE_PRICE))
                    .andExpect(jsonPath("$.data.remainingBalance").value(REMAINING_BALANCE));
        }

        @Test
        @DisplayName("실패 - 이미 구매한 회차")
        void purchaseEpisode_alreadyPurchased() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED.getMessage()));
        }

        @Test
        @DisplayName("실패 - 포인트 부족")
        void purchaseEpisode_insufficientPoint() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage()));
        }

        @Test
        @DisplayName("실패 - 회차를 찾을 수 없음")
        void purchaseEpisode_episodeNotFound() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(EpisodeExceptionEnum.EPISODE_NOT_FOUND.getMessage()));
        }

        @Test
        @DisplayName("실패 - 무료 회차 구매 시도")
        void purchaseEpisode_freeEpisode() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.EPISODE_FREE_NO_PURCHASE));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(EpisodeExceptionEnum.EPISODE_FREE_NO_PURCHASE.getMessage()));
        }

        @Test
        @DisplayName("실패 - 구매 불가능한 회차 (미발행/삭제)")
        void purchaseEpisode_notAvailable() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE.getMessage()));
        }

        @Test
        @DisplayName("실패 - 결제 처리 중 (락 획득 실패)")
        void purchaseEpisode_paymentProcessing() throws Exception {
            // given
            given(purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING));

            // when & then
            mockMvc.perform(post("/api/episodes/{episodeId}/purchase", EPISODE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage()));
        }
    }

    // =========================================================
    // 소설 전체 구매 테스트
    // =========================================================
    @Nested
    @DisplayName("POST /api/novels/{novelId}/episodes/purchase - 소설 전체 구매")
    class PurchaseAllEpisodes {

        @Test
        @DisplayName("성공 - 소설 전체 구매 성공 (10% 할인 적용)")
        void purchaseAllEpisodes_success() throws Exception {
            // given
            NovelBulkPurchaseResponse mockResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID,
                    10,              // 총 회차 수
                    2000,            // 원가
                    10,              // 할인율
                    200,             // 할인 금액
                    1800,            // 최종 가격
                    REMAINING_BALANCE - 1800,
                    List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/novels/{novelId}/episodes/purchase", NOVEL_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("소설 전체 구매 성공"))
                    .andExpect(jsonPath("$.data.novelId").value(NOVEL_ID))
                    .andExpect(jsonPath("$.data.totalEpisodes").value(10))
                    .andExpect(jsonPath("$.data.originalPrice").value(2000))
                    .andExpect(jsonPath("$.data.discountRate").value(10))
                    .andExpect(jsonPath("$.data.discountAmount").value(200))
                    .andExpect(jsonPath("$.data.finalPrice").value(1800))
                    .andExpect(jsonPath("$.data.purchasedEpisodeIds").isArray())
                    .andExpect(jsonPath("$.data.purchasedEpisodeIds.length()").value(10));
        }

        @Test
        @DisplayName("실패 - 구매 가능한 회차 없음")
        void purchaseAllEpisodes_noPurchasableEpisodes() throws Exception {
            // given
            given(purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES));

            // when & then
            mockMvc.perform(post("/api/novels/{novelId}/episodes/purchase", NOVEL_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES.getMessage()));
        }

        @Test
        @DisplayName("실패 - 포인트 부족")
        void purchaseAllEpisodes_insufficientPoint() throws Exception {
            // given
            given(purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT));

            // when & then
            mockMvc.perform(post("/api/novels/{novelId}/episodes/purchase", NOVEL_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage()));
        }

        @Test
        @DisplayName("실패 - 결제 처리 중 (락 획득 실패)")
        void purchaseAllEpisodes_paymentProcessing() throws Exception {
            // given
            given(purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING));

            // when & then
            mockMvc.perform(post("/api/novels/{novelId}/episodes/purchase", NOVEL_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage()));
        }
    }
}