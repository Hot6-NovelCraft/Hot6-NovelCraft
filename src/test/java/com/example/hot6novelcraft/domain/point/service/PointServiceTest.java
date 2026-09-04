package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.point.entity.Point;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.repository.PointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PointService 테스트")
class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 10000L;
    private static final Long INITIAL_BALANCE = 5000L;

    private Point createMockPoint(Long userId, Long balance) {
        Point point = mock(Point.class);
        given(point.getUserId()).willReturn(userId);
        given(point.getBalance()).willReturn(balance);
        return point;
    }

    // =========================================================
    // 포인트 충전 테스트
    // =========================================================
    @Nested
    @DisplayName("charge() - 포인트 충전")
    class ChargeTest {

        @Test
        @DisplayName("성공 - 기존 Point 존재 시 충전")
        void charge_existingPoint_success() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            // when
            pointService.charge(USER_ID, AMOUNT);

            // then
            verify(existingPoint, times(1)).charge(AMOUNT);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));

            // PointHistory 검증
            ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(historyCaptor.capture());
            PointHistory savedHistory = historyCaptor.getValue();

            assertThat(savedHistory.getUserId()).isEqualTo(USER_ID);
            assertThat(savedHistory.getAmount()).isEqualTo(AMOUNT);
            assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.CHARGE);
            assertThat(savedHistory.getDescription()).isEqualTo("포인트 충전");
        }

        @Test
        @DisplayName("성공 - Point 없을 시 새로 생성 후 충전")
        void charge_newPoint_success() {
            // given
            Point newPoint = createMockPoint(USER_ID, 0L);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.empty());
            given(pointRepository.save(any(Point.class)))
                    .willReturn(newPoint);

            // when
            pointService.charge(USER_ID, AMOUNT);

            // then
            verify(pointRepository, times(1)).save(any(Point.class));
            verify(newPoint, times(1)).charge(AMOUNT);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));
        }

        @Test
        @DisplayName("성공 - PointHistory에 novelId, episodeId는 null")
        void charge_pointHistory_nullIds() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            // when
            pointService.charge(USER_ID, AMOUNT);

            // then
            ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(historyCaptor.capture());
            PointHistory savedHistory = historyCaptor.getValue();

            assertThat(savedHistory.getNovelId()).isNull();
            assertThat(savedHistory.getEpisodeId()).isNull();
        }
    }

    // =========================================================
    // 포인트 차감 테스트
    // =========================================================
    @Nested
    @DisplayName("deduct() - 포인트 차감")
    class DeductTest {

        @Test
        @DisplayName("성공 - 잔액 충분 시 차감")
        void deduct_sufficientBalance_success() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            // when
            pointService.deduct(USER_ID, 1000L);

            // then
            verify(existingPoint, times(1)).deduct(1000L);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));

            // PointHistory 검증
            ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(historyCaptor.capture());
            PointHistory savedHistory = historyCaptor.getValue();

            assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.REFUND);
            assertThat(savedHistory.getDescription()).isEqualTo("환불 차감");
        }

        @Test
        @DisplayName("실패 - Point가 존재하지 않음")
        void deduct_pointNotFound_throwsException() {
            // given
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.deduct(USER_ID, AMOUNT))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_POINT_NOT_FOUND.getMessage());

            verify(pointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 잔액 부족")
        void deduct_insufficientBalance_throwsException() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            Long largeAmount = INITIAL_BALANCE + 1000L;

            // when & then
            assertThatThrownBy(() -> pointService.deduct(USER_ID, largeAmount))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());

            verify(existingPoint, never()).deduct(anyLong());
            verify(pointHistoryRepository, never()).save(any());
        }
    }

    // =========================================================
    // 포인트 복구 테스트
    // =========================================================
    @Nested
    @DisplayName("compensateDeduct() - 포인트 복구")
    class CompensateDeductTest {

        @Test
        @DisplayName("성공 - 기존 Point 존재 시 복구")
        void compensateDeduct_existingPoint_success() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            // when
            pointService.compensateDeduct(USER_ID, AMOUNT);

            // then
            verify(existingPoint, times(1)).charge(AMOUNT);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));

            // PointHistory 검증
            ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(historyCaptor.capture());
            PointHistory savedHistory = historyCaptor.getValue();

            assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.CHARGE);
            assertThat(savedHistory.getDescription()).isEqualTo("환불 오류 복구");
        }

        @Test
        @DisplayName("성공 - Point 없을 시 새로 생성 후 복구")
        void compensateDeduct_newPoint_success() {
            // given
            Point newPoint = createMockPoint(USER_ID, 0L);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.empty());
            given(pointRepository.save(any(Point.class)))
                    .willReturn(newPoint);

            // when
            pointService.compensateDeduct(USER_ID, AMOUNT);

            // then
            verify(pointRepository, times(1)).save(any(Point.class));
            verify(newPoint, times(1)).charge(AMOUNT);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));
        }
    }

    // =========================================================
    // 포인트 잔액 조회 테스트
    // =========================================================
    @Nested
    @DisplayName("getBalance() - 포인트 잔액 조회")
    class GetBalanceTest {

        @Test
        @DisplayName("성공 - Point 존재 시 잔액 반환")
        void getBalance_existingPoint_returnsBalance() {
            // given
            Point existingPoint = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(existingPoint));

            // when
            Long balance = pointService.getBalance(USER_ID);

            // then
            assertThat(balance).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("성공 - Point 없을 시 0 반환")
        void getBalance_pointNotFound_returnsZero() {
            // given
            given(pointRepository.findByUserId(USER_ID))
                    .willReturn(Optional.empty());

            // when
            Long balance = pointService.getBalance(USER_ID);

            // then
            assertThat(balance).isEqualTo(0L);
        }
    }
}
