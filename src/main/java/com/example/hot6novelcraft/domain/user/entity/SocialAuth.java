package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor
@Table(name = "social_accounts")
public class SocialAuth extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProviderSns provider;

    private String providerId;

    public static SocialAuth register(ProviderSns provider, String providerId, Long userId) {
      return SocialAuth.builder()
              .provider(provider)
              .providerId(providerId)
              .userId(userId)
              .build();
    }

    public void updateProviderId(String providerId) {
        this.providerId = providerId;
    }
}
