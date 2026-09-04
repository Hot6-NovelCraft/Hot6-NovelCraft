package com.example.hot6novelcraft.domain.payment.repository;

import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaymentKey(String paymentKey);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    // 재검증 배치용: 특정 상태 + 생성 시각 기준
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold);

    List<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);

}
