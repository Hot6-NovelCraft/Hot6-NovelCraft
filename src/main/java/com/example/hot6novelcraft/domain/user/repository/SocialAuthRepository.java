package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.SocialAuth;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAuthRepository extends JpaRepository<SocialAuth, Long> {

    // 구글 등 프로바이더와 해당 고유 ID로 이미 연결된 정보가 있는지 확인
    boolean existsByProviderAndProviderId(ProviderSns provider, String providerId);

    // 특정 유저의 소셜 인증 정보가 필요할 때 (userId PK 직접 참조)
    Optional<SocialAuth> findByUserId(Long userId);

    // 프로바이더 정보로 소셜 인증 정보 조회
    Optional<SocialAuth> findByProviderAndProviderId(ProviderSns provider, String providerId);
}
