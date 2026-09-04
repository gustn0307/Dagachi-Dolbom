package com.dagachi.backend.common.ai.dto;

/**
 * FastAPI의 제보 요약 결과를 Spring Boot가 받을 때 사용하는
 * 내부 통신용 Response DTO입니다.
 *
 * FastAPI 응답:
 *
 * {
 *   "summary": "...",
 *   "model": "gpt-4o-mini"
 * }
 *
 * 이후 AIAnalysis 저장 기능을 구현할 때
 * summary는 resultJson에,
 * model은 AIAnalysis.modelName에 활용할 수 있습니다.
 */
public record AiReportSummaryResponse(
        String summary,
        String model
) {
}