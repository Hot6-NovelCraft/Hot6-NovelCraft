package com.example.hot6novelcraft.domain.event.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record EventCreateRequest(

        @NotBlank(message = "이벤트 제목을 입력해 주세요")
        @Size(max = 200, message = "제목은 200자 이내로 입력해 주세요")
        String title,

        String description,

        @NotNull(message = "리워드 포인트를 입력해 주세요")
        @Min(value = 1, message = "리워드 포인트는 1 이상이어야 합니다")
        Long rewardPoints,

        @NotNull(message = "최대 참여 인원을 입력해 주세요")
        @Min(value = 1, message = "최대 참여 인원은 1명 이상이어야 합니다")
        Long maxParticipants,

        @NotNull(message = "이벤트 시작일을 입력해 주세요")
        LocalDateTime startedAt,

        @NotNull(message = "이벤트 종료일을 입력해 주세요")
        LocalDateTime endedAt

) {
    @AssertTrue(message = "이벤트 시작일은 종료일보다 이전이어야 합니다")
    public boolean isValidPeriod() {
        if (startedAt == null || endedAt == null) return true;
        return startedAt.isBefore(endedAt);
    }
}
