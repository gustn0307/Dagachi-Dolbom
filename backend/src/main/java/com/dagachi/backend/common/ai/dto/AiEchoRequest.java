package com.dagachi.backend.common.ai.dto;

/*
 * Spring Boot에서 FastAPI의 echo API로 전달할 요청 DTO입니다.
 *
 * FastAPI의 EchoRequest Schema:
 *
 * {
 *   "message": "Spring Boot 통신 테스트"
 * }
 *
 * 와 동일한 JSON 구조를 사용합니다.
 */
public record AiEchoRequest(
        String message
) {
}