package com.dagachi.backend.common.ai.client;

import com.dagachi.backend.common.ai.dto.AiEchoRequest;
import com.dagachi.backend.common.ai.dto.AiEchoResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {

    private final RestClient aiServiceRestClient;

    /*
     * AiServiceConfig에서 Bean으로 등록한 RestClient를
     * 생성자 주입받습니다.
     */
    public AiServiceClient(
            RestClient aiServiceRestClient
    ) {
        this.aiServiceRestClient = aiServiceRestClient;
    }

    /*
     * Spring Boot에서 FastAPI의 echo 테스트 API를 호출합니다.
     *
     * 실제 요청:
     *
     * POST http://localhost:8000/api/v1/test/echo
     *
     * Request Body:
     * {
     *   "message": "..."
     * }
     *
     * Response:
     * {
     *   "message": "...",
     *   "source": "fastapi"
     * }
     *
     * 현재는 Spring Boot ↔ FastAPI 통신이 정상적으로
     * 이루어지는지 확인하기 위한 테스트용 메서드입니다.
     *
     * 이후 실제 AI 기능에서도 같은 방식으로
     * 분석/챗봇/RAG API를 호출하게 됩니다.
     */
    public AiEchoResponse echo(
            String message
    ) {
        AiEchoRequest request =
                new AiEchoRequest(message);

        return aiServiceRestClient
                .post()
                .uri("/api/v1/test/echo")
                .body(request)
                .retrieve()
                .body(AiEchoResponse.class);
    }
}