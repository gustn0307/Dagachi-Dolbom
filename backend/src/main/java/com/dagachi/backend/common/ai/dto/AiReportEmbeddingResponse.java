package com.dagachi.backend.common.ai.dto;

/**
 * FastAPI의 제보 embedding 생성 결과를 받는 DTO입니다.
 */
public record AiReportEmbeddingResponse(
        float[] embedding,
        String model
) {
}