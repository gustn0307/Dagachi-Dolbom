package com.dagachi.backend.common.ai.dto;

/**
 * Spring Boot가 FastAPI의 제보 요약 API를 호출할 때 사용하는
 * 내부 통신용 Request DTO입니다.
 *
 * Frontend에서 직접 사용하는 DTO가 아니며,
 * Spring Boot -> FastAPI 통신에만 사용합니다.
 *
 * 현재 첫 AI 요약 구현에서는 개인정보, 주소, 좌표, 이미지 등을
 * OpenAI에 전달하지 않고 제보 원문(content)만 전달합니다.
 */
public record AiReportSummaryRequest(
        String content
) {
}