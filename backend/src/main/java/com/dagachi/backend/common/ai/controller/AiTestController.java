package com.dagachi.backend.common.ai.controller;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiEchoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/test")
public class AiTestController {

    private final AiServiceClient aiServiceClient;

    public AiTestController(
            AiServiceClient aiServiceClient
    ) {
        this.aiServiceClient = aiServiceClient;
    }

    /*
     * Spring Boot → FastAPI 실제 HTTP 통신을 확인하기 위한
     * 임시 테스트 endpoint입니다.
     *
     * 사용 예:
     *
     * GET /api/ai/test/echo?message=hello
     *
     * 호출 흐름:
     *
     * Postman
     *   ↓
     * Spring Boot :8080
     *   ↓
     * AiServiceClient
     *   ↓ HTTP POST
     * FastAPI :8000
     *   ↓
     * Spring Boot
     *   ↓
     * Postman
     *
     * 실제 AI 기능 구현이 시작되면
     * 이 테스트 Controller는 제거할 수 있습니다.
     */
    @GetMapping("/echo")
    public ResponseEntity<AiEchoResponse> echo(
            @RequestParam String message
    ) {
        AiEchoResponse response =
                aiServiceClient.echo(message);

        return ResponseEntity.ok(response);
    }
}