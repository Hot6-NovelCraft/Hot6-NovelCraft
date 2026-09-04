package com.example.hot6novelcraft.domain.mentor.scheduler;

import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorCareerHistory;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorCareerHistoryRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MentorCareerLevelScheduler {

    private static final long INTRODUCTION_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_LIKES = 50L;
    private static final long INTERMEDIATE_MIN_EPISODES = 100L;
    private static final long INTERMEDIATE_MIN_LIKES = 100L;
    private static final int CHUNK_SIZE = 100;

    private final MentorRepository mentorRepository;
    private final MentorCareerHistoryRepository mentorCareerHistoryRepository;
    private final NovelRepository novelRepository;
    private final EpisodeRepository episodeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 매일 자정 멘토 등급 자동 조정
     * PROFICIENT(전문)는 관리자 수동 승급이므로 배치 대상 제외
     * 청크 단위(100명) 처리로 메모리 부하 개선
     * ID ASC 정렬로 페이지 경계 고정 - 행 누락/중복 방지
     * 청크 처리 후 flush/clear로 persistence context 누적 방지
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void adjustCareerLevels() {
        log.info("[MentorCareerLevelScheduler] 멘토 등급 자동 조정 배치 시작");

        int pageNumber = 0;
        int totalUpgradedCount = 0;
        Page<Mentor> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, CHUNK_SIZE, Sort.by("id").ascending());
            page = mentorRepository.findAllByStatusAndCareerLevelNot(
                    MentorStatus.APPROVED, CareerLevel.PROFICIENT, pageable
            );

            int upgradedCount = processChunk(page.getContent());
            totalUpgradedCount += upgradedCount;
            pageNumber++;

            // persistence context 누적 방지
            entityManager.flush();
            entityManager.clear();

        } while (!page.isLast());

        log.info("[MentorCareerLevelScheduler] 배치 완료 - 총 {}명 등급 조정", totalUpgradedCount);
    }

    private int processChunk(List<Mentor> mentors) {
        int upgradedCount = 0;

        for (Mentor mentor : mentors) {
            CareerLevel previousLevel = mentor.getCareerLevel();
            CareerLevel newLevel = resolveNewLevel(mentor);

            if (newLevel != null && newLevel != previousLevel) {
                MentorCareerHistory history = MentorCareerHistory.create(
                        mentor.getId(),
                        previousLevel,
                        newLevel,
                        buildChangeReason(newLevel)
                );
                mentor.upgradeCareerLevel(newLevel);
                mentorCareerHistoryRepository.save(history);
                upgradedCount++;
                log.info("[MentorCareerLevelScheduler] mentorId={} {} → {}",
                        mentor.getId(), previousLevel, newLevel);
            }
        }

        return upgradedCount;
    }

    private CareerLevel resolveNewLevel(Mentor mentor) {
        List<Long> novelIds = novelRepository.findNovelIdsByAuthorId(mentor.getUserId());
        if (novelIds.isEmpty()) return null;

        long publishedCount = episodeRepository.countByNovelIdInAndStatus(novelIds, EpisodeStatus.PUBLISHED);
        long totalLikes = episodeRepository.sumLikeCountByNovelIdIn(novelIds);

        return switch (mentor.getCareerLevel()) {
            case INTRODUCTION -> {
                if (publishedCount >= INTRODUCTION_MIN_EPISODES) yield CareerLevel.ELEMENTARY;
                yield null;
            }
            case ELEMENTARY -> {
                if (publishedCount >= ELEMENTARY_MIN_EPISODES && totalLikes >= ELEMENTARY_MIN_LIKES) {
                    yield CareerLevel.INTERMEDIATE;
                }
                yield null;
            }
            default -> null;
        };
    }

    private String buildChangeReason(CareerLevel newLevel) {
        return switch (newLevel) {
            case ELEMENTARY -> "PUBLISHED 에피소드 50회 이상 달성으로 자동 승급";
            case INTERMEDIATE -> "PUBLISHED 에피소드 100회 이상 및 좋아요 100개 이상 달성으로 자동 승급";
            default -> "자동 승급";
        };
    }
}