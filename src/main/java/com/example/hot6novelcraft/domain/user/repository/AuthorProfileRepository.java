package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorProfileRepository extends JpaRepository<AuthorProfile, Long> {

    Optional<AuthorProfile> findByUserId(Long userId);
}
