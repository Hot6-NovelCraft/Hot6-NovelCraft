package com.example.hot6novelcraft.domain.recommendationAi.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.recommendationAi.dto.RecommendationResponse;
import com.example.hot6novelcraft.domain.recommendationAi.service.RecommendationFacade;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationFacade recommendationFacade;

    /**
     * ===== 메인 페이지 소설 추천 ====
     * 로그인 : 행동 기반 개인 추천
     * 비로그인 + 신규 회원 : 트렌드 기반 추천
     */
    @GetMapping
    public ResponseEntity<BaseResponse<RecommendationResponse>> getRecommendations(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        RecommendationResponse response = (userDetails != null)
                ? recommendationFacade.getPersonalizedRecommendations(userDetails.getUser().getId())
                : recommendationFacade.getTrendRecommendation();

        String message = switch (response.recommendationType()) {
            case "PERSONALIZED" -> "맞춤 추천 소설입니다";
            case "TREND" -> "지금 인기 있는 소설 입니다";
            default -> "추천 소설입니다";
        };

        return ResponseEntity.ok(BaseResponse.success("200", message, response));
    }
}
