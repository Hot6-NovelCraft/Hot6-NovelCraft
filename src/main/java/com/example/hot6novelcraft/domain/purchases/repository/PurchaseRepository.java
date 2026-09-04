package com.example.hot6novelcraft.domain.purchases.repository;

import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
}