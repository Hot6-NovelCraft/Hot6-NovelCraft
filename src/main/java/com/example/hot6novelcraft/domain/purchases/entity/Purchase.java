package com.example.hot6novelcraft.domain.purchases.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "purchases")
public class Purchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private PurchaseType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long paymentId;

    private Purchase(Long userId, PurchaseType type, Long amount, Long paymentId) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.paymentId = paymentId;
    }

    public static Purchase create(Long userId, PurchaseType type, Long amount, Long paymentId) {
        return new Purchase(userId, type, amount, paymentId);
    }
}
