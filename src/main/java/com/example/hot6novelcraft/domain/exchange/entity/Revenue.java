package com.example.hot6novelcraft.domain.exchange.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "revenues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Revenue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    private Long episodeId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private Integer balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevenueType type;

    private Revenue(Long authorId, Long episodeId, Integer amount, Integer balance, RevenueType type) {
        this.authorId = authorId;
        this.episodeId = episodeId;
        this.amount = amount;
        this.balance = balance;
        this.type = type;
    }

    public static Revenue create(Long authorId, Long episodeId, Integer amount, Integer balance, RevenueType type) {
        return new Revenue(authorId, episodeId, amount, balance, type);
    }

    /**
     * 환전 차감용 Revenue 생성
     */
    public static Revenue ofWithdrawal(Long authorId, Integer amount, Integer balanceAfter) {
        return new Revenue(authorId, null, amount, balanceAfter, RevenueType.WITHDRAWAL);
    }
}