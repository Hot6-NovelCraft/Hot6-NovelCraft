package com.example.hot6novelcraft.domain.event.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipateResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.service.UserEventService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class UserEventController {

    private final UserEventService userEventService;

    /**
     * 이벤트 목록 조회 (진행중/종료 필터링) - Redis 캐싱 적용
     * GET /api/events?status=ONGOING
     */
    @GetMapping
    public BaseResponse<PageResponse<EventSummaryResponse>> getEventList(
            @RequestParam(defaultValue = "ONGOING") EventStatus status,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<EventSummaryResponse> page = userEventService.getEventList(status, pageable);
        return BaseResponse.success("200", "이벤트 목록을 성공적으로 조회했습니다", PageResponse.register(page));
    }

    /**
     * 이벤트 상세 조회
     * GET /api/events/{eventId}
     */
    @GetMapping("/{eventId}")
    public BaseResponse<EventDetailResponse> getEventDetail(
            @PathVariable Long eventId
    ) {
        EventDetailResponse response = userEventService.getEventDetail(eventId);
        return BaseResponse.success("200", "이벤트 상세 정보를 성공적으로 조회했습니다", response);
    }

    /**
     * 이벤트 참여 신청 - 선착순 처리 + 즉시 포인트 지급
     * POST /api/events/{eventId}/participants
     */
    @PostMapping("/{eventId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<EventParticipateResponse> participate(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long eventId
    ) {
        Long userId = userDetails.getUser().getId();
        EventParticipateResponse response = userEventService.participate(eventId, userId);
        return BaseResponse.success("201", "이벤트 참여 신청이 완료되었습니다", response);
    }
}