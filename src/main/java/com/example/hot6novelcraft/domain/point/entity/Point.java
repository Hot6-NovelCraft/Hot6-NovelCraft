package com.example.hot6novelcraft.domain.point.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "points")
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Long balance;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Point(Long userId, Long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public static Point create(Long userId) {
        return new Point(userId, 0L);
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void charge(Long amount) {
        this.balance += amount;
    }

    public void deduct(Long amount) {
        this.balance -= amount;
    }
}
