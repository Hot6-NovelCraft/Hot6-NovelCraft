package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.point.entity.Point;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 포인트 충전
     * - userId에 해당하는 Point가 없으면 최초 생성
     */
    @Transactional
    public void charge(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> pointRepository.save(Point.create(userId)));

        point.charge(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.CHARGE, "포인트 충전")
        );
    }

    /**
     * 포인트 차감 (환불 시 회수)
     */
    @Transactional
    public void deduct(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

        if (point.getBalance() < amount) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        point.deduct(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.REFUND, "환불 차감")
        );
    }

    /**
     * 포인트 복구 (환불 진행 중 PortOne 오류 발생 시 선차감된 포인트 보상)
     */
    @Transactional
    public void compensateDeduct(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> pointRepository.save(Point.create(userId)));

        point.charge(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.CHARGE, "환불 오류 복구")
        );
    }

    /**
     * 포인트 잔액 조회
     */
    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        return pointRepository.findByUserId(userId)
                .map(Point::getBalance)
                .orElse(0L);
    }

    /**
     * 이벤트 보상 포인트 지급
     * - 선착순 참여 성공 시 즉시 호출
     */
    @Transactional
    public void chargeEventReward(Long userId, Long amount, Long eventId) {
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> pointRepository.save(Point.create(userId)));

        point.charge(amount);

        pointHistoryRepository.save(
                PointHistory.create(userId, null, null, amount, PointHistoryType.EVENT,
                        "이벤트 참여 보상 지급 (eventId: " + eventId + ")")
        );
    }

    /**
     * AI 리뷰 사용 포인트 차감
     * - AI 리뷰 호출 성공 후 호출
     */
    @Transactional
    public void deductForAi(Long userId, Long amount, Long episodeId) {
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

        if (point.getBalance() < amount) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        point.deduct(amount);

        pointHistoryRepository.save(
                PointHistory.create(
                        userId,
                        null,
                        episodeId,
                        amount,
                        PointHistoryType.AI,
                        "AI 리뷰 사용 (episodeId: " + episodeId + ")"
                )
        );
    }
}