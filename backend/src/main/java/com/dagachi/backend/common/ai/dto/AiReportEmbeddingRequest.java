package com.dagachi.backend.common.ai.dto;

/**
 * Spring Boot가 FastAPI에 제보 embedding 생성을 요청할 때 사용하는 DTO입니다.
 *
 * 개인정보와 위치정보는 전달하지 않고 Report.content만 전달합니다.
 */
public record AiReportEmbeddingRequest(
        String content
) {
}