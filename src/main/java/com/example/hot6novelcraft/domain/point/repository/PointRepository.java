package com.example.hot6novelcraft.domain.point.repository;

import com.example.hot6novelcraft.domain.point.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointRepository extends JpaRepository<Point, Long> {

    Optional<Point> findByUserId(Long userId);
}