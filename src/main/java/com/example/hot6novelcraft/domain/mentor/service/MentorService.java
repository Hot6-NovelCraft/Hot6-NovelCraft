package com.example.hot6novelcraft.domain.mentor.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorRegisterRequest;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorUpdateRequest;
import com.example.hot6novelcraft.domain.mentor.dto.response.*;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorFeedbackRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipReviewRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorService {

    private static final long INTRODUCTION_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_EPISODES = 50L;
    private static final long ELEMENTARY_MIN_LIKES = 50L;
    private static final long INTERMEDIATE_MIN_EPISODES = 100L;
    private static final long INTERMEDIATE_MIN_LIKES = 100L;

    private final MentorRepository mentorRepository;
    private final MentorshipRepository mentorshipRepository;
    private final MentorFeedbackRepository mentorFeedbackRepository;
    private final UserRepository userRepository;
    private final NovelRepository novelRepository;
    private final EpisodeRepository episodeRepository;
    private final ObjectMapper objectMapper;
    private final MentorshipReviewRepository mentorshipReviewRepository;
    private final AdminCacheService adminCacheService;

    /**
     * 멘토 등록 신청
     */
    @Transactional
    public MentorRegisterResponse register(Long userId, MentorRegisterRequest request) {
        if (mentorRepository.existsByUserIdAndStatus(userId, MentorStatus.PENDING)) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_PENDING_EXISTS);
        }
        if (mentorRepository.existsByUserIdAndStatus(userId, MentorStatus.APPROVED)) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_ALREADY_APPROVED);
        }

        MentorStatus initialStatus = resolveInitialStatus(userId, request.careerLevel());

        Mentor mentor = Mentor.create(
                userId,
                request.careerLevel(),
                toJson(request.mainGenres()),
                toJson(request.specialFields()),
                toJson(request.mentoringStyles()),
                request.bio(),
                request.awardsCareer(),
                request.maxMentees(),
                request.allowInstant(),
                request.preferredMenteeDesc(),
                initialStatus
        );

        try {
            Mentor saved = mentorRepository.save(mentor);
            adminCacheService.incrementNewMentorsToday();
            return MentorRegisterResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_ALREADY_APPROVED);
        }
    }

    /**
     * 멘토 정보 수정
     */
    @Transactional
    public MentorUpdateResponse update(Long userId, MentorUpdateRequest request) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        if (request.careerHistory() != null && request.careerHistory().isBlank()) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_CAREER_REQUIRED);
        }

        mentor.update(
                request.introduction(),
                toJsonForUpdate(request.mainGenres()),
                toJsonForUpdate(request.specialFields()),
                toJsonForUpdate(request.mentoringStyles()),
                request.careerHistory(),
                request.maxMentees(),
                request.allowInstant(),
                request.preferredMenteeDesc()
        );

        return MentorUpdateResponse.from(mentor.getId(), LocalDateTime.now());
    }

    /**
     * 내 멘토 프로필 조회
     */
    @Transactional(readOnly = true)
    public MentorProfileResponse getMyProfile(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return MentorProfileResponse.from(mentor);
    }

    /**
     * 내 멘토 등록 상태 조회
     */
    @Transactional(readOnly = true)
    public MentorStatusResponse getMyStatus(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return MentorStatusResponse.from(mentor);
    }

    /**
     * 내 멘토링 통계 조회
     */
    @Transactional(readOnly = true)
    public MentorStatisticsResponse getStatistics(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        long pendingCount = mentorshipRepository.countByMentorIdAndStatus(
                mentor.getId(), MentorshipStatus.PENDING);

        long thisMonthAcceptedCount = mentorshipRepository.countAcceptedThisMonth(
                mentor.getId(), startOfMonth);

        long thisMonthRejectedCount = mentorshipRepository.countRejectedThisMonth(
                mentor.getId(), startOfMonth);

        return MentorStatisticsResponse.of(pendingCount, thisMonthAcceptedCount, thisMonthRejectedCount);
    }

    /**
     * 내 멘티 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MenteeInfoResponse> getMyMentees(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return mentorshipRepository.findAllByMentorIdAndStatus(mentor.getId(), MentorshipStatus.ACCEPTED)
                .stream()
                .map(mentorship -> {
                    String menteeName = userRepository.findByIdAndIsDeletedFalse(mentorship.getMenteeId())
                            .map(User::getNickname)
                            .orElse("알 수 없는 사용자");

                    String novelTitle = novelRepository.findById(mentorship.getCurrentNovelId())
                            .map(Novel::getTitle)
                            .orElse("알 수 없는 소설");

                    LocalDateTime lastFeedbackAt = mentorFeedbackRepository
                            .findTopByMentorshipIdOrderByCreatedAtDesc(mentorship.getId())
                            .map(MentorFeedback::getCreatedAt)
                            .orElse(null);

                    return MenteeInfoResponse.of(mentorship, menteeName, novelTitle, lastFeedbackAt);
                })
                .toList();
    }

    /**
     * 내 멘티 목록 조회 v2 - QueryDSL JOIN으로 N+1 개선
     */
    @Transactional(readOnly = true)
    public List<MenteeInfoResponse> getMyMenteesV2(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return mentorRepository.findMenteesWithDetails(mentor.getId());
    }

    /**
     * 내 멘토링 통계 상세 조회
     */
    @Transactional(readOnly = true)
    public MentorStatisticsDetailResponse getStatisticsDetail(Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        long totalMentees = mentorshipReviewRepository.countTotalMenteesByMentorId(mentor.getId());
        long completedSessions = mentorshipReviewRepository.countCompletedSessionsByMentorId(mentor.getId());
        Double averageSatisfaction = mentorshipReviewRepository.findAverageRatingByMentorId(mentor.getId());

        return MentorStatisticsDetailResponse.of(totalMentees, completedSessions, averageSatisfaction);
    }

    /**
     * careerLevel 기준 초기 상태 결정
     */
    private MentorStatus resolveInitialStatus(Long userId, CareerLevel careerLevel) {
        if (careerLevel == CareerLevel.PROFICIENT) {
            return MentorStatus.PENDING;
        }

        List<Long> novelIds = novelRepository.findNovelIdsByAuthorId(userId);
        if (novelIds.isEmpty()) {
            return MentorStatus.PENDING;
        }

        long publishedCount = episodeRepository.countByNovelIdInAndStatus(novelIds, EpisodeStatus.PUBLISHED);
        long totalLikes = episodeRepository.sumLikeCountByNovelIdIn(novelIds);

        return switch (careerLevel) {
            case INTRODUCTION -> publishedCount >= INTRODUCTION_MIN_EPISODES
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            case ELEMENTARY -> (publishedCount >= ELEMENTARY_MIN_EPISODES && totalLikes >= ELEMENTARY_MIN_LIKES)
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            case INTERMEDIATE -> (publishedCount >= INTERMEDIATE_MIN_EPISODES && totalLikes >= INTERMEDIATE_MIN_LIKES)
                    ? MentorStatus.APPROVED : MentorStatus.PENDING;
            default -> MentorStatus.PENDING;
        };
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_JSON_SERIALIZE_FAILED);
        }
    }

    private String toJsonForUpdate(List<String> list) {
        if (list == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_JSON_SERIALIZE_FAILED);
        }
    }
}