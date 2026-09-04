package com.example.hot6novelcraft.domain.point.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_histories")
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long novelId;

    private Long episodeId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 20)
    @Enumerated(value = EnumType.STRING)
    private PointHistoryType type;

    private String description;

    private PointHistory(Long userId, Long novelId, Long episodeId, Long amount, PointHistoryType type, String description) {
        this.userId = userId;
        this.novelId = novelId;
        this.episodeId = episodeId;
        this.amount = amount;
        this.type = type;
        this.description = description;
    }

    public static PointHistory create(Long userId, Long novelId, Long episodeId, Long amount, PointHistoryType type, String description) {
        return new PointHistory(userId, novelId, episodeId, amount, type, description);
    }
}
