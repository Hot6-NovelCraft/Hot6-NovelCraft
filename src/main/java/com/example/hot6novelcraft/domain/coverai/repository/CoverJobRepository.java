package com.example.hot6novelcraft.domain.coverai.repository;

import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverJobRepository extends JpaRepository<CoverJob, Long> {
    Optional<CoverJob> findByJobId(String jobId);
}