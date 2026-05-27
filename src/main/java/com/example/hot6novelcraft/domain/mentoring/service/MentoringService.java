package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentor.repository.MentorFeedbackRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentoringFeedbackRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringFeedbackResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentoringService {

    private final MentorshipRepository mentorshipRepository;
    private final MentorRepository mentorRepository;
    private final UserRepository userRepository;
    private final NovelRepository novelRepository;
    private final MentorFeedbackRepository mentorFeedbackRepository;

    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;

    // =====================================================================
    // 공통 로직 (V1 / V2 동일)
    // =====================================================================

    @Transactional
    public void acceptMentee(Long mentoringId, Long menteeId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (!mentorship.getMenteeId().equals(menteeId)) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH);
        }
        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED);
        }

        mentor.decreaseSlot();
        mentorship.approve();

        String mentorNickname = userRepository.findById(userId).map(User::getNickname).orElse("멘토");
        eventPublisher.publishEvent(NotificationEvent.mentorshipAccepted(menteeId, mentorNickname, mentoringId));
    }

    @Transactional
    public void rejectMentee(Long mentoringId, Long menteeId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (!mentorship.getMenteeId().equals(menteeId)) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH);
        }
        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED);
        }

        mentorship.reject();

        String mentorName = userRepository.findById(userId).map(User::getNickname).orElse("멘토");
        eventPublisher.publishEvent(
                NotificationEvent.mentorshipRejected(mentorship.getMenteeId(), mentorName, mentoringId));
    }

    @Transactional
    public String getManuscriptDownloadUrl(Long mentoringId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (mentorship.getManuscriptUrl() == null) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_MANUSCRIPT_NOT_FOUND);
        }

        mentorship.increaseManuscriptDownloadCount();

        // S3 Presigned URL 발급 (1시간 유효)
        return fileUploadService.generateManuscriptPresignedUrl(mentorship.getManuscriptUrl());
    }

    @Transactional
    public void completeMentoring(Long mentoringId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (mentorship.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_ACCEPTED);
        }

        mentor.increaseSlot();
        mentorship.complete();

        // 멘티에게 종료 알림
        String mentorName = userRepository.findById(userId).map(User::getNickname).orElse("멘토");
        eventPublisher.publishEvent(
                NotificationEvent.mentorshipCompleted(mentorship.getMenteeId(), mentorName, mentoringId));
    }

    // =====================================================================
    // 멘티 전용
    // =====================================================================

    // 멘티가 멘토링 종료
    @Transactional
    public void completeMentoringByMentee(Long mentoringId, Long menteeId) {
        Mentorship mentorship = mentorshipRepository.findByIdAndMenteeId(mentoringId, menteeId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));
        if (mentorship.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_ACCEPTED);
        }
        Mentor mentor = mentorRepository.findById(mentorship.getMentorId())
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));
        mentor.increaseSlot();
        mentorship.complete();

        // 멘토에게 종료 알림
        String menteeName = userRepository.findById(menteeId).map(User::getNickname).orElse("멘티");
        eventPublisher.publishEvent(
                NotificationEvent.mentorshipCompleted(mentor.getUserId(), menteeName, mentoringId));
    }

    // 멘티가 자신의 멘토링 상세 조회 (피드백 포함)
    public MentoringDetailResponse getMentoringDetailForMentee(Long mentoringId, Long menteeId) {
        Mentorship mentorship = mentorshipRepository.findByIdAndMenteeId(mentoringId, menteeId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        Mentor mentor = mentorRepository.findById(mentorship.getMentorId())
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        String mentorName = userRepository.findByIdAndIsDeletedFalse(mentor.getUserId())
                .map(User::getNickname).orElse("알 수 없는 멘토");
        String menteeName = userRepository.findByIdAndIsDeletedFalse(menteeId)
                .map(User::getNickname).orElse("알 수 없는 사용자");
        String novelTitle = mentorship.getCurrentNovelId() != null
                ? novelRepository.findByIdAndIsDeletedFalse(mentorship.getCurrentNovelId())
                .map(Novel::getTitle).orElse("알 수 없는 소설")
                : "선택 없음";

        List<MentoringDetailResponse.FeedbackInfo> feedbacks = mentorFeedbackRepository
                .findAllByMentorshipIdOrderByCreatedAtAsc(mentoringId)
                .stream()
                .map(MentoringDetailResponse.FeedbackInfo::from)
                .toList();

        return MentoringDetailResponse.of(mentorship, mentorName, menteeName, novelTitle, feedbacks);
    }

    // =====================================================================
    // getReceivedMentorings
    // =====================================================================

    /**
     * V1 - 내 멘토링 접수 목록 조회
     * soft-delete 미적용 (findById 사용)
     */
    public Page<MentoringReceivedResponse> getReceivedMentorings(Long userId, Pageable pageable) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(mentor.getId(), pageable)
                .map(mentorship -> {
                    String menteeName = userRepository.findByIdAndIsDeletedFalse(mentorship.getMenteeId())
                            .map(User::getNickname)
                            .orElse("알 수 없는 사용자");

                    // V1: soft-delete 미적용 — 삭제된 소설 제목 노출 가능
                    String title = novelRepository.findById(mentorship.getCurrentNovelId())
                            .map(Novel::getTitle)
                            .orElse("알 수 없는 소설");

                    return MentoringReceivedResponse.of(mentorship, menteeName, title);
                });
    }

    /**
     * V2 - 내 멘토링 접수 목록 조회
     * soft-delete 적용 + N+1 개선 (QueryDSL JOIN)
     */
    public Page<MentoringReceivedResponse> getReceivedMentoringsV2(Long userId, Pageable pageable) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        return mentorshipRepository.findReceivedMentoringsWithDetails(mentor.getId(), pageable);
    }

    // =====================================================================
    // getMentoringDetail
    // =====================================================================

    /**
     * V1 - 멘토링 상세 조회
     * soft-delete 미적용 (findById 사용)
     */
    public MentoringDetailResponse getMentoringDetail(Long mentoringId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }

        String mentorName = userRepository.findByIdAndIsDeletedFalse(userId)
                .map(User::getNickname)
                .orElse("알 수 없는 사용자");

        String menteeName = userRepository.findByIdAndIsDeletedFalse(mentorship.getMenteeId())
                .map(User::getNickname)
                .orElse("알 수 없는 사용자");

        // V1: soft-delete 미적용 — 삭제된 소설 제목 노출 가능
        String novelTitle = novelRepository.findById(mentorship.getCurrentNovelId())
                .map(Novel::getTitle)
                .orElse("알 수 없는 소설");

        List<MentoringDetailResponse.FeedbackInfo> feedbacks = mentorFeedbackRepository
                .findAllByMentorshipIdOrderByCreatedAtAsc(mentoringId)
                .stream()
                .map(MentoringDetailResponse.FeedbackInfo::from)
                .toList();

        return MentoringDetailResponse.of(mentorship, mentorName, menteeName, novelTitle, feedbacks);
    }

    /**
     * V2 - 멘토링 상세 조회
     * soft-delete 적용 (findByIdAndIsDeletedFalse 사용) — 삭제된 소설 제목 노출 방지
     */
    public MentoringDetailResponse getMentoringDetailV2(Long mentoringId, Long userId) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }

        String mentorName = userRepository.findByIdAndIsDeletedFalse(userId)
                .map(User::getNickname)
                .orElse("알 수 없는 사용자");

        String menteeName = userRepository.findByIdAndIsDeletedFalse(mentorship.getMenteeId())
                .map(User::getNickname)
                .orElse("알 수 없는 사용자");

        // V2: soft-delete 적용 — 삭제된 소설은 "알 수 없는 소설" 반환
        String novelTitle = novelRepository.findByIdAndIsDeletedFalse(mentorship.getCurrentNovelId())
                .map(Novel::getTitle)
                .orElse("알 수 없는 소설");

        List<MentoringDetailResponse.FeedbackInfo> feedbacks = mentorFeedbackRepository
                .findAllByMentorshipIdOrderByCreatedAtAsc(mentoringId)
                .stream()
                .map(MentoringDetailResponse.FeedbackInfo::from)
                .toList();

        return MentoringDetailResponse.of(mentorship, mentorName, menteeName, novelTitle, feedbacks);
    }

    // =====================================================================
    // createFeedback
    // =====================================================================

    /**
     * V1 - 멘토링 피드백 작성
     * 동시성 보호 없음 (일반 findById 사용)
     */
    @Transactional
    public MentoringFeedbackResponse createFeedback(Long mentoringId, Long userId,
                                                    MentoringFeedbackRequest request) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        // V1: 동시성 보호 없음 — 동시 요청 시 같은 sessionNumber 중복 저장 가능
        Mentorship mentorship = mentorshipRepository.findById(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (mentorship.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_FEEDBACK_ONLY_ACCEPTED);
        }

        int nextSession = mentorship.getTotalSessions() + 1;

        MentorFeedback feedback = MentorFeedback.create(
                mentoringId, mentor.getId(),
                request.title(), nextSession, request.content()
        );
        mentorFeedbackRepository.save(feedback);
        mentorship.increaseSession();

        return MentoringFeedbackResponse.from(feedback);
    }

    /**
     * V2 - 멘토링 피드백 작성
     * 비관적 락(findByIdWithLock) + 유니크 제약으로 sessionNumber 동시성 보호
     */
    @Transactional
    public MentoringFeedbackResponse createFeedbackV2(Long mentoringId, Long userId,
                                                      MentoringFeedbackRequest request) {
        Mentor mentor = mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));

        // V2: 비관적 락 — 동시 요청 직렬화로 sessionNumber 중복 방지
        Mentorship mentorship = mentorshipRepository.findByIdWithLock(mentoringId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        if (!mentorship.getMentorId().equals(mentor.getId())) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_UNAUTHORIZED);
        }
        if (mentorship.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_FEEDBACK_ONLY_ACCEPTED);
        }

        int nextSession = mentorship.getTotalSessions() + 1;

        MentorFeedback feedback = MentorFeedback.create(
                mentoringId, mentor.getId(),
                request.title(), nextSession, request.content()
        );

        try {
            mentorFeedbackRepository.save(feedback);
        } catch (DataIntegrityViolationException e) {
            // V2: 유니크 제약 충돌 — 동시 요청으로 같은 회차 저장 시도 방어
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_SESSION_CONFLICT);
        }

        mentorship.increaseSession();

        return MentoringFeedbackResponse.from(feedback);
    }
}