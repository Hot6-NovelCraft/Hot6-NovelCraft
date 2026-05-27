package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipCreateRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.*;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipReviewRepository;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorshipService {

    private final MentorshipRepository mentorshipRepository;
    private final MentorRepository mentorRepository;
    private final UserRepository userRepository;
    private final MentorshipReviewRepository mentorshipReviewRepository;

    private final FileUploadService fileUploadService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 멘토링 신청(멘티)
    @Transactional
    public MentorshipCreateResponse applyMentorship(Long menteeId, MentorshipCreateRequest request) {

        // 작가 권한 확인
        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (mentee.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_AUTHOR);
        }

        // 멘토 조회 (못 찾으면 NOT_FOUND) - mentorId는 Mentor 엔티티 PK
        Mentor mentor = mentorRepository.findById(request.mentorId())
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        // 본인한테 신청 불가
        if (mentor.getUserId().equals(menteeId)) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_SELF_APPLY);
        }

        // 이미 진행중인 멘토링 있으면 신청 불가 (1:1 제약)
        boolean alreadyExists = mentorshipRepository.existsByMenteeIdAndStatusIn(
                menteeId,
                List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED)
        );
        if (alreadyExists) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_ALREADY_EXISTS);
        }

        if (mentor.getMaxMentees() <= 0) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_SLOT_FULL);
        }

        // 멘토링 신청 생성
        Mentorship mentorship = Mentorship.create(
                mentor.getId(),
                menteeId,
                request.currentNovelId(),
                request.motivation(),
                request.manuscriptUrl()
        );

        Mentorship saved = mentorshipRepository.save(mentorship);
        log.info("[Mentorship] 멘토링 신청 완료 menteeId={} mentorId={}", menteeId, request.mentorId());

        // 즉시수락 여부 처리
        if (Boolean.TRUE.equals(mentor.getAllowInstant())) {
            mentor.decreaseSlot();
            saved.approve();
            log.info("[Mentorship] 즉시수락 처리 menteeId={} mentorId={}", menteeId, request.mentorId());
            String mentorNickname = userRepository.findById(mentor.getUserId())
                    .map(com.example.hot6novelcraft.domain.user.entity.User::getNickname).orElse("멘토");
            eventPublisher.publishEvent(NotificationEvent.mentorshipAccepted(menteeId, mentorNickname, saved.getId()));
        } else {
            eventPublisher.publishEvent(NotificationEvent.mentorshipRequest(mentor.getUserId(), mentee.getNickname(), saved.getId()));
        }

        return MentorshipCreateResponse.from(saved.getId());
    }

    // 멘토링 원고 업로드
    public String uploadManuscript(MultipartFile file, Long menteeId) {

        // 작가 권한 확인
        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (mentee.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_AUTHOR);
        }

        return fileUploadService.uploadManuscript(file);
    }

    // 멘토 목록 조회(멘티 시점) - 필터: 장르,등급 / 본인 멘토 제외
    @Transactional(readOnly = true)
    public Page<MentorshipListResponse> getMentorList(String genre, CareerLevel careerLevel, Long currentUserId, Pageable pageable) {

        Page<MentorWithNickname> mentors = mentorshipRepository.findMentorList(genre, careerLevel, currentUserId, pageable);

        return mentors.map(m -> MentorshipListResponse.of(
                m.mentorId(),
                m.nickname(),
                m.careerLevel(),
                fromJson(m.mainGenres()),
                fromJson(m.specialFields()),
                fromJson(m.mentoringStyle()),
                m.awardsCareer(),
                m.maxMentees()
        ));
    }

    // 멘토 상세 조회(멘티 시점)
    @Transactional(readOnly = true)
    public MentorshipDetailResponse getMentorDetail(Long mentorId) {

        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        String nickname = userRepository.findById(mentor.getUserId())
                .map(User::getNickname)
                .orElse("알 수 없는 사용자");

        return MentorshipDetailResponse.of(
                mentor.getId(),
                nickname,
                mentor.getCareerLevel(),
                fromJson(mentor.getMainGenres()),
                fromJson(mentor.getSpecialFields()),
                fromJson(mentor.getMentoringStyle()),
                mentor.getAwardsCareer(),
                mentor.getBio(),
                mentor.getMaxMentees()
        );
    }

    /**
     * V1 - 내 멘토링 이력 조회
     * N+1 존재 - 멘토링 수만큼 mentorRepository + userRepository 반복 쿼리 발생
     */
    // 내 멘토링 조회(멘티 시점)
    @Transactional(readOnly = true)
    public List<MentorshipHistoryResponse> getMyHistory(Long menteeId, MentorshipStatus status) {

        // 작가 권한 확인
        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (mentee.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_AUTHOR);
        }

        // 상태 필터 여부에 따라 조회
        List<Mentorship> mentorships;
        if (status != null) {
            mentorships = mentorshipRepository.findAllByMenteeIdAndStatusOrderByCreatedAtDesc(menteeId, status);
        } else {
            mentorships = mentorshipRepository.findAllByMenteeIdOrderByCreatedAtDesc(menteeId);
        }

        // Mentorship -> DTO 변환
        return mentorships.stream()
                .map(m -> {
                    Mentor mentor = mentorRepository.findById(m.getMentorId()).orElse(null);
                    String mentorNickname = mentor == null ? "알 수 없는 멘토"
                            : userRepository.findById(mentor.getUserId())
                            .map(User::getNickname).orElse("알 수 없는 멘토");
                    boolean hasReview = mentorshipReviewRepository.existsByMentorshipId(m.getId());

                    return new MentorshipHistoryResponse(
                            m.getId(),
                            m.getMentorId(),
                            mentorNickname,
                            m.getStatus(),
                            m.getCreatedAt(),
                            m.getMotivation(),
                            m.getManuscriptUrl(),
                            m.getCurrentNovelId(),
                            hasReview
                    );
                })
                .toList();
    }

    // V2: N+1 개선 - QueryDSL JOIN으로 멘토 닉네임 한번에 조회 + hasReview 배치 처리
    @Transactional(readOnly = true)
    public List<MentorshipHistoryResponse> getMyHistoryV2(Long menteeId, MentorshipStatus status) {
        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (mentee.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_AUTHOR);
        }

        List<MentorshipHistoryResponse> list =
                mentorshipRepository.findMyHistoryWithMentorNickname(menteeId, status);
        if (list.isEmpty()) return list;

        List<Long> allIds = list.stream().map(MentorshipHistoryResponse::mentorshipId).toList();
        Set<Long> reviewedIds = new HashSet<>(mentorshipReviewRepository.findReviewedMentorshipIds(allIds));
        if (reviewedIds.isEmpty()) return list;

        return list.stream()
                .map(h -> reviewedIds.contains(h.mentorshipId())
                        ? new MentorshipHistoryResponse(h.mentorshipId(), h.mentorId(), h.mentorNickname(),
                        h.status(), h.appliedAt(), h.motivation(), h.manuscriptUrl(),
                        h.currentNovelId(), true)
                        : h)
                .toList();
    }

    // 멘토링 신청 취소(멘티) - PENDING 상태만 가능
    @Transactional
    public void cancelMentorship(Long menteeId, Long mentorshipId) {

        Mentorship mentorship = mentorshipRepository.findByIdAndMenteeId(mentorshipId, menteeId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED));

        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_CANCEL_NOT_ALLOWED);
        }

        mentorship.cancel();
        log.info("[Mentorship] 멘토링 신청 취소 menteeId={} mentorshipId={}", menteeId, mentorshipId);
    }

    // JSON 문자열을 List<String>으로 변환
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}