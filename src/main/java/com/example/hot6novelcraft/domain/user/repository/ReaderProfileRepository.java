package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {

    Optional<ReaderProfile> findByUserId(Long userId);

}
