package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.BlacklistToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface BlacklistTokenRepository extends JpaRepository<BlacklistToken, Long> {

    // Redis 장애 시 DB에서 블랙리스트 여부 조회
    boolean existsByToken(String token);

    // 매일 스케쥴러가 만료된 토큰 정리 (DB 용량 관리)
    @Modifying
    @Query("DELETE FROM BlacklistToken bt WHERE bt.expiredAt < :now")
    void deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}
