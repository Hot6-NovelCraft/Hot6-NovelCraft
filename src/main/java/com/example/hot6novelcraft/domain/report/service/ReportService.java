package com.example.hot6novelcraft.domain.report.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ReportExceptionEnum;
import com.example.hot6novelcraft.domain.episode.entity.EpisodeComment;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeCommentRepository;
import com.example.hot6novelcraft.domain.report.dto.request.ReportCreateRequest;
import com.example.hot6novelcraft.domain.report.dto.response.ReportCreateResponse;
import com.example.hot6novelcraft.domain.report.entity.Report;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import com.example.hot6novelcraft.domain.report.repository.ReportRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final long DUPLICATE_REPORT_HOURS = 24;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final EpisodeCommentRepository episodeCommentRepository;

    // 신고 등록
    @Transactional
    public ReportCreateResponse createReport(ReportCreateRequest request, UserDetailsImpl userDetails) {

        Long reporterId = userDetails.getUser().getId();

        // 본인 신고 방지
        if (Objects.equals(reporterId, request.targetId())) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_SELF_FORBIDDEN);
        }

        // targetType 별 commentId 유효성 검증
        validateCommentIdByTargetType(request.targetType(), request.commentId());

        // 신고 대상 유저 존재 확인
        validateTargetUserExists(request.targetId());

        // 댓글 신고일 경우 댓글 존재 + 작성자 일치 확인
        if (request.targetType() == ReportTargetType.COMMENTS) {
            validateTargetComment(request.commentId(), request.targetId());
        }

        // 24시간 내 중복 신고 검증
        validateDuplicateReport(reporterId, request);

        // 신고 저장
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(request.targetId())
                .commentId(request.commentId())
                .reason(request.reason())
                .targetType(request.targetType())
                .build();

        Report savedReport = reportRepository.save(report);

        log.info("[신고 접수] reporterId={}, targetId={}, targetType={}, reportId={}",
                reporterId, request.targetId(), request.targetType(), savedReport.getId());

        return ReportCreateResponse.from(savedReport.getId());
    }


    // -----------------------------------------공통 매서드--------------------------------------------

    // targetType 상황별 commentId 유효성 검증
    private void validateCommentIdByTargetType(ReportTargetType targetType, Long commentId) {
        // 댓글 신고일때 댓글아이디O
        if (targetType == ReportTargetType.COMMENTS && commentId == null) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_COMMENT_ID_REQUIRED);
        }
        // 멘토멘티 신고일때 댓글아이디X
        if (targetType == ReportTargetType.USERS && commentId != null) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_COMMENT_ID_NOT_ALLOWED);
        }
    }

    // 신고 대상 유저 존재 확인
    private void validateTargetUserExists(Long targetUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_TARGET_USER_NOT_FOUND);
        }
    }

    // 댓글 존재 + 작성자 일치 검증 (위변조 방지)
    private void validateTargetComment(Long commentId, Long targetUserId) {
        EpisodeComment comment = episodeCommentRepository.findById(commentId)
                .orElseThrow(() -> new ServiceErrorException(ReportExceptionEnum.REPORT_TARGET_COMMENT_NOT_FOUND));

        // 댓글 작성자와 신고대상 유저 일치하는지 검증
        if (!Objects.equals(comment.getUserId(), targetUserId)) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_COMMENT_USER_MISMATCH);
        }
    }

    // 24시간 내 중복신고 검증
    private void validateDuplicateReport(Long reporterId, ReportCreateRequest request) {

        LocalDateTime since = LocalDateTime.now().minusHours(DUPLICATE_REPORT_HOURS);

        boolean isDuplicate;

        if (request.targetType() == ReportTargetType.COMMENTS) {
            // 댓글 신고 - 같은 댓글 기준
            isDuplicate = reportRepository.existsByReporterIdAndCommentIdAndCreatedAtAfter(
                    reporterId, request.commentId(), since);
        } else {
            // 유저 신고 - 같은 유저 기준
            isDuplicate = reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndCreatedAtAfter(
                    reporterId, request.targetId(), request.targetType(), since);
        }

        if (isDuplicate) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_DUPLICATE);
        }
    }
}