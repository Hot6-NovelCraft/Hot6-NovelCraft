package com.example.hot6novelcraft.domain.coverai.client;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.CoverExceptionEnum;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class GeminiClient {

    @Value("${gemini.api-key}")
    private String apiKey;

    /*
     * 2026-05-07 반영:
     * API 명세서에 따라 모델명을 'gemini-3.1-flash-image-preview'로 수정.
     * responseModalities 설정을 명세서와 동일하게 "TEXT", "IMAGE"로 유지.
     */
    public byte[] generateImage(String prompt) {
        // Client 생성 시 API Key를 넘겨주는 방식 확인 필요 (명세서엔 기본 생성자였지만 프로젝트 구조 유지)
        try (Client client = Client.builder()
                .apiKey(apiKey)
                .build()) {

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseModalities(List.of("TEXT", "IMAGE"))
                    .build();

            // 명세서에 명시된 이미지 생성 지원 모델명 사용
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.1-flash-image-preview",
                    prompt,
                    config
            );

            for (Part part : response.parts()) {
                // 이미지는 inlineData 영역에 바이너리로 들어옴
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        log.info("[Gemini] 이미지 생성 성공 - 바이너리 데이터 추출");
                        return blob.data().get(); // byte[] 반환
                    }
                }
            }

            throw CoverExceptionEnum.IMAGE_GENERATION_FAILED.toException();

        } catch (ServiceErrorException e) {
            throw e; // 그대로 rethrow
        } catch (Exception e) {
            log.error("[Gemini] 이미지 생성 실패: {}", e.getMessage(), e);
            throw CoverExceptionEnum.IMAGE_GENERATION_FAILED.toException();
        }
    }
}