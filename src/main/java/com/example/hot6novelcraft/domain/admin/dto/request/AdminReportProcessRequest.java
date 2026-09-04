package com.example.hot6novelcraft.domain.admin.dto.request;

import com.example.hot6novelcraft.domain.admin.entity.enums.ResultStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminReportProcessRequest(

        @NotNull(message = "처리 결과는 필수입니다.")
        ResultStatus resultStatus,

        @NotBlank(message = "처리 메모를 입력해주세요.")
        @Size(max = 100, message = "처리 메모는 최대 100자까지 입력 가능합니다.")
        String adminMemo
) {
}