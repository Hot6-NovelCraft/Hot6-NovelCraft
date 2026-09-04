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
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    ReportRepository reportRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    EpisodeCommentRepository episodeCommentRepository;

    @InjectMocks
    ReportService reportService;

    // ==================== Mock ====================

    // 신고자 Mock (id = 1)
    private UserDetailsImpl 신고자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 댓글 Mock
    private EpisodeComment 댓글(Long commentId, Long writerId) {
        EpisodeComment comment = mock(EpisodeComment.class);
        given(comment.getId()).willReturn(commentId);
        given(comment.getUserId()).willReturn(writerId);
        return comment;
    }

    // 댓글 신고 요청 Mock
    private ReportCreateRequest 댓글신고요청(Long targetId, Long commentId) {
        ReportCreateRequest request = mock(ReportCreateRequest.class);
        given(request.targetType()).willReturn(ReportTargetType.COMMENTS);
        given(request.targetId()).willReturn(targetId);
        given(request.commentId()).willReturn(commentId);
        given(request.reason()).willReturn("신고 사유 테스트");
        return request;
    }

    // 유저 신고 요청 Mock
    private ReportCreateRequest 유저신고요청(Long targetId) {
        ReportCreateRequest request = mock(ReportCreateRequest.class);
        given(request.targetType()).willReturn(ReportTargetType.USERS);
        given(request.targetId()).willReturn(targetId);
        given(request.commentId()).willReturn(null);
        given(request.reason()).willReturn("신고 사유 테스트");
        return request;
    }

    // 저장된 Report Mock
    private Report 저장된신고(Long reportId) {
        Report report = mock(Report.class);
        given(report.getId()).willReturn(reportId);
        return report;
    }

    // ==================== 신고 등록 - 성공 ====================

    @Test
    void 댓글신고_등록_성공() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 댓글신고요청(2L, 10L);
        EpisodeComment comment = 댓글(10L, 2L);
        Report savedReport = 저장된신고(100L);

        given(userRepository.existsById(2L)).willReturn(true);
        given(episodeCommentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndCommentIdAndCreatedAtAfter(
                anyLong(), anyLong(), any(LocalDateTime.class))).willReturn(false);
        given(reportRepository.save(any(Report.class))).willReturn(savedReport);

        ReportCreateResponse response = reportService.createReport(request, userDetails);

        assertEquals(100L, response.reportId());
    }

    @Test
    void 유저신고_등록_성공() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 유저신고요청(2L);
        Report savedReport = 저장된신고(101L);

        given(userRepository.existsById(2L)).willReturn(true);
        given(reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndCreatedAtAfter(
                anyLong(), anyLong(), any(ReportTargetType.class), any(LocalDateTime.class))).willReturn(false);
        given(reportRepository.save(any(Report.class))).willReturn(savedReport);

        ReportCreateResponse response = reportService.createReport(request, userDetails);

        assertEquals(101L, response.reportId());
    }

    // ==================== 신고 등록 - 실패 ====================

    @Test
    void 본인신고_실패() {
        UserDetailsImpl userDetails = 신고자(); // id=1
        ReportCreateRequest request = 유저신고요청(1L); // targetId=1 (본인)

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_SELF_FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void 댓글신고인데_commentId_누락_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 댓글신고요청(2L, null); // commentId 누락

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_COMMENT_ID_REQUIRED, exception.getErrorCode());
    }

    @Test
    void 유저신고인데_commentId_들어옴_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = mock(ReportCreateRequest.class);
        given(request.targetType()).willReturn(ReportTargetType.USERS);
        given(request.targetId()).willReturn(2L);
        given(request.commentId()).willReturn(10L); // USERS인데 commentId 들어옴
        given(request.reason()).willReturn("사유");

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_COMMENT_ID_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void 신고대상_유저_존재하지_않음_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 유저신고요청(999L);

        given(userRepository.existsById(999L)).willReturn(false);

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_TARGET_USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 신고대상_댓글_존재하지_않음_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 댓글신고요청(2L, 999L);

        given(userRepository.existsById(2L)).willReturn(true);
        given(episodeCommentRepository.findById(999L)).willReturn(Optional.empty());

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_TARGET_COMMENT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 댓글작성자와_targetId_불일치_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 댓글신고요청(2L, 10L); // targetId=2
        EpisodeComment comment = 댓글(10L, 99L); // 실제 작성자=99

        given(userRepository.existsById(2L)).willReturn(true);
        given(episodeCommentRepository.findById(10L)).willReturn(Optional.of(comment));

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_COMMENT_USER_MISMATCH, exception.getErrorCode());
    }

    @Test
    void 댓글_24시간_중복신고_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 댓글신고요청(2L, 10L);
        EpisodeComment comment = 댓글(10L, 2L);

        given(userRepository.existsById(2L)).willReturn(true);
        given(episodeCommentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndCommentIdAndCreatedAtAfter(
                anyLong(), anyLong(), any(LocalDateTime.class))).willReturn(true); // 중복

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_DUPLICATE, exception.getErrorCode());
    }

    @Test
    void 유저_24시간_중복신고_실패() {
        UserDetailsImpl userDetails = 신고자();
        ReportCreateRequest request = 유저신고요청(2L);

        given(userRepository.existsById(2L)).willReturn(true);
        given(reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndCreatedAtAfter(
                anyLong(), anyLong(), any(ReportTargetType.class), any(LocalDateTime.class))).willReturn(true); // 중복

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> reportService.createReport(request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_DUPLICATE, exception.getErrorCode());
    }
}