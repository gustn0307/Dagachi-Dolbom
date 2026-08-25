package com.dagachi.backend.common.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServiceConfig {

    /*
     * FastAPI AI 서버의 기본 주소입니다.
     *
     * 로컬 개발 환경에서는 기본값으로
     * http://localhost:8000 을 사용합니다.
     *
     * 배포 환경에서는 환경변수 AI_SERVICE_BASE_URL을 통해
     * 실제 FastAPI 서버 주소를 주입할 예정입니다.
     */
    @Value("${ai.service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    /*
     * Spring Boot가 FastAPI 서버를 호출할 때 사용할
     * 공통 RestClient 객체를 Bean으로 등록합니다.
     *
     * baseUrl을 지정해두면 이후 실제 요청에서는
     * /api/v1/... 경로만 작성하면 됩니다.
     *
     * 예:
     * aiServiceRestClient.post()
     *     .uri("/api/v1/test/echo")
     */
    @Bean
    public RestClient aiServiceRestClient() {
        return RestClient.builder()
                .baseUrl(aiServiceBaseUrl)
                .build();
    }
}