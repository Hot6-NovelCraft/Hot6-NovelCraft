package com.example.hot6novelcraft.domain.report.dto.request;

import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(

        @NotNull(message = "신고 대상 타입은 필수입니다.")
        ReportTargetType targetType,

        @NotNull(message = "신고 대상 유저 ID는 필수입니다.")
        Long targetId,

        Long commentId,  // 댓글 신고일 때만 필수 (서비스에서 검증)

        @NotBlank(message = "신고 사유를 입력해주세요.")
        @Size(max = 500, message = "신고 사유는 최대 500자까지 입력 가능합니다.")
        String reason
) {
}