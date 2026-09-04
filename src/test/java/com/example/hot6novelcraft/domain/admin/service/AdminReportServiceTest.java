package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ReportExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.request.AdminReportProcessRequest;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportListResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportProcessResponse;
import com.example.hot6novelcraft.domain.admin.entity.ReportManagement;
import com.example.hot6novelcraft.domain.admin.entity.enums.ResultStatus;
import com.example.hot6novelcraft.domain.admin.repository.AdminReportRepository;
import com.example.hot6novelcraft.domain.admin.repository.ReportManagementRepository;
import com.example.hot6novelcraft.domain.report.entity.Report;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock
    AdminReportRepository adminReportRepository;

    @Mock
    ReportManagementRepository reportManagementRepository;

    @InjectMocks
    AdminReportService adminReportService;

    // ==================== Mock 헬퍼 ====================

    // 관리자 Mock
    private UserDetailsImpl 관리자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 신고 Mock
    private Report 신고(ReportStatus status) {
        Report report = mock(Report.class);
        given(report.getId()).willReturn(1L);
        given(report.getTargetId()).willReturn(2L);
        given(report.getStatus()).willReturn(status);
        return report;
    }

    // 신고처리 요청 Mock
    private AdminReportProcessRequest 신고처리요청(ResultStatus resultStatus) {
        AdminReportProcessRequest request = mock(AdminReportProcessRequest.class);
        given(request.resultStatus()).willReturn(resultStatus);
        given(request.adminMemo()).willReturn("테스트 처리 메모");
        return request;
    }

    // 신고처리 기록 Mock
    private ReportManagement 신고처리기록() {
        ReportManagement management = mock(ReportManagement.class);
        given(management.getId()).willReturn(10L);
        given(management.getReportId()).willReturn(1L);
        given(management.getTargetId()).willReturn(2L);
        given(management.getResultStatus()).willReturn(ResultStatus.WARNING);
        given(management.getAdminMemo()).willReturn("테스트 처리 메모");
        given(management.getCreatedAt()).willReturn(LocalDateTime.now());
        return management;
    }

    // 신고 목록 Mock
    private Page<AdminReportListResponse> 신고목록(int size) {
        List<AdminReportListResponse> list = List.of(
                new AdminReportListResponse(1L, 2L, "신고자닉네임", 3L, "대상닉네임",
                        null, "신고사유", ReportStatus.REGISTER, ReportTargetType.USERS, LocalDateTime.now()),
                new AdminReportListResponse(2L, 4L, "신고자닉네임2", 5L, "대상닉네임2",
                        10L, "댓글신고사유", ReportStatus.REGISTER, ReportTargetType.COMMENTS, LocalDateTime.now())
        );
        return new PageImpl<>(list.subList(0, Math.min(size, list.size())));
    }

    // ==================== 신고 목록 조회 ====================

    @Test
    void 신고목록조회_전체_성공() {
        Pageable pageable = PageRequest.of(0, 20);
        given(adminReportRepository.findReportList(null, null, pageable))
                .willReturn(신고목록(2));

        PageResponse<AdminReportListResponse> response =
                adminReportService.getReportList(null, null, pageable);

        assertEquals(2, response.content().size());
    }

    @Test
    void 신고목록조회_status필터_성공() {
        Pageable pageable = PageRequest.of(0, 20);
        given(adminReportRepository.findReportList(ReportStatus.REGISTER, null, pageable))
                .willReturn(신고목록(2));

        PageResponse<AdminReportListResponse> response =
                adminReportService.getReportList(ReportStatus.REGISTER, null, pageable);

        assertEquals(2, response.content().size());
    }

    @Test
    void 신고목록조회_targetType필터_성공() {
        Pageable pageable = PageRequest.of(0, 20);
        given(adminReportRepository.findReportList(null, ReportTargetType.COMMENTS, pageable))
                .willReturn(신고목록(1));

        PageResponse<AdminReportListResponse> response =
                adminReportService.getReportList(null, ReportTargetType.COMMENTS, pageable);

        assertEquals(1, response.content().size());
    }

    @Test
    void 신고목록조회_복합필터_성공() {
        Pageable pageable = PageRequest.of(0, 20);
        given(adminReportRepository.findReportList(ReportStatus.REGISTER, ReportTargetType.USERS, pageable))
                .willReturn(신고목록(1));

        PageResponse<AdminReportListResponse> response =
                adminReportService.getReportList(ReportStatus.REGISTER, ReportTargetType.USERS, pageable);

        assertEquals(1, response.content().size());
    }

    // ==================== 신고 처리 - 성공 ====================

    @Test
    void 신고처리_경고_성공() {
        UserDetailsImpl userDetails = 관리자();
        AdminReportProcessRequest request = 신고처리요청(ResultStatus.WARNING);
        Report report = 신고(ReportStatus.REGISTER);
        ReportManagement management = 신고처리기록();

        given(adminReportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportManagementRepository.existsByReportId(1L)).willReturn(false);
        given(reportManagementRepository.save(any(ReportManagement.class))).willReturn(management);
        given(reportManagementRepository.countByTargetId(anyLong())).willReturn(1L);

        AdminReportProcessResponse response =
                adminReportService.processReport(1L, request, userDetails);

        assertEquals(10L, response.reportManagementId());
        assertEquals(1L, response.reportId());
        assertEquals(ResultStatus.WARNING, response.resultStatus());
    }

    @Test
    void 신고처리_밴_성공() {
        UserDetailsImpl userDetails = 관리자();
        AdminReportProcessRequest request = 신고처리요청(ResultStatus.BANNED);
        Report report = 신고(ReportStatus.REGISTER);
        ReportManagement management = mock(ReportManagement.class);
        given(management.getId()).willReturn(11L);
        given(management.getReportId()).willReturn(1L);
        given(management.getTargetId()).willReturn(2L);
        given(management.getResultStatus()).willReturn(ResultStatus.BANNED);
        given(management.getAdminMemo()).willReturn("테스트 처리 메모");
        given(management.getCreatedAt()).willReturn(LocalDateTime.now());

        given(adminReportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportManagementRepository.existsByReportId(1L)).willReturn(false);
        given(reportManagementRepository.save(any(ReportManagement.class))).willReturn(management);
        given(reportManagementRepository.countByTargetId(anyLong())).willReturn(1L);

        AdminReportProcessResponse response =
                adminReportService.processReport(1L, request, userDetails);

        assertEquals(ResultStatus.BANNED, response.resultStatus());
    }

    // ==================== 신고 처리 - 실패 ====================

    @Test
    void 신고처리_존재하지않는신고_실패() {
        UserDetailsImpl userDetails = 관리자();
        AdminReportProcessRequest request = 신고처리요청(ResultStatus.WARNING);

        given(adminReportRepository.findById(999L)).willReturn(Optional.empty());

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> adminReportService.processReport(999L, request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 신고처리_이미완료된신고_실패() {
        UserDetailsImpl userDetails = 관리자();
        AdminReportProcessRequest request = 신고처리요청(ResultStatus.WARNING);
        Report report = 신고(ReportStatus.COMPLETION); // 이미 완료

        given(adminReportRepository.findById(1L)).willReturn(Optional.of(report));

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> adminReportService.processReport(1L, request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_ALREADY_COMPLETED, exception.getErrorCode());
    }

    @Test
    void 신고처리_중복처리_실패() {
        UserDetailsImpl userDetails = 관리자();
        AdminReportProcessRequest request = 신고처리요청(ResultStatus.WARNING);
        Report report = 신고(ReportStatus.REGISTER);

        given(adminReportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportManagementRepository.existsByReportId(1L)).willReturn(true); // 이미 처리기록 있음

        ServiceErrorException exception = assertThrows(
                ServiceErrorException.class,
                () -> adminReportService.processReport(1L, request, userDetails)
        );

        assertEquals(ReportExceptionEnum.REPORT_ALREADY_COMPLETED, exception.getErrorCode());
    }
}