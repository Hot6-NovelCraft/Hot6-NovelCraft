package com.example.hot6novelcraft.domain.event.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.event.dto.request.EventCreateRequest;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipantResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.service.AdminEventService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final AdminEventService adminEventService;

    /**
     * 이벤트 생성
     * POST /api/admin/events
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<EventDetailResponse> createEvent(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody @Valid EventCreateRequest request
    ) {
        Long adminId = userDetails.getUser().getId();
        EventDetailResponse response = adminEventService.createEvent(adminId, request);
        return BaseResponse.success("201", "이벤트가 성공적으로 생성되었습니다", response);
    }

    /**
     * 이벤트 목록 조회 (진행중/종료 필터링) - 캐싱 없이 실시간 조회
     * GET /api/admin/events?status=ONGOING
     */
    @GetMapping
    public BaseResponse<PageResponse<EventSummaryResponse>> getEventList(
            @RequestParam(defaultValue = "ONGOING") EventStatus status,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<EventSummaryResponse> page = adminEventService.getEventList(status, pageable);
        return BaseResponse.success("200", "이벤트 목록을 성공적으로 조회했습니다", PageResponse.register(page));
    }

    /**
     * 이벤트 상세 조회
     * GET /api/admin/events/{eventId}
     */
    @GetMapping("/{eventId}")
    public BaseResponse<EventDetailResponse> getEventDetail(
            @PathVariable Long eventId
    ) {
        EventDetailResponse response = adminEventService.getEventDetail(eventId);
        return BaseResponse.success("200", "이벤트 상세 정보를 성공적으로 조회했습니다", response);
    }

    /**
     * 이벤트별 참여자 목록 조회
     * GET /api/admin/events/{eventId}/participants
     */
    @GetMapping("/{eventId}/participants")
    public BaseResponse<PageResponse<EventParticipantResponse>> getParticipants(
            @PathVariable Long eventId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<EventParticipantResponse> page = adminEventService.getParticipants(eventId, pageable);
        return BaseResponse.success("200", "이벤트 참여자 목록을 성공적으로 조회했습니다", PageResponse.register(page));
    }
}