package com.dagachi.backend.common.ai.dto;

/*
 * FastAPI의 echo API 응답을
 * Spring Boot에서 역직렬화하기 위한 DTO입니다.
 *
 * FastAPI 응답:
 *
 * {
 *   "message": "Spring Boot 통신 테스트",
 *   "source": "fastapi"
 * }
 */
public record AiEchoResponse(
        String message,
        String source
) {
}