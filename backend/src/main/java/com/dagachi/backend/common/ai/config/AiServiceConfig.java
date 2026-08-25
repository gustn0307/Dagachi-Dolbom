package com.dagachi.backend.common.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiServiceConfig {

    /*
     * FastAPI AI 서버의 기본 주소입니다.
     *
     * 로컬 개발 환경에서는 기본값으로
     * http://localhost:8000 을 사용합니다.
     *
     * 배포 환경에서는 환경변수 AI_SERVICE_BASE_URL을 통해
     * 실제 FastAPI 서버 주소를 주입합니다.
     */
    @Value("${ai.service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    /*
     * FastAPI 서버와 연결을 맺을 때 기다릴 최대 시간(ms)입니다.
     *
     * 서버가 내려가 있거나 네트워크 문제가 있을 때
     * Spring 요청이 장시간 대기하는 것을 방지합니다.
     */
    @Value("${ai.service.connect-timeout:3000}")
    private long connectTimeoutMillis;

    /*
     * FastAPI 서버에 요청을 보낸 뒤
     * 응답을 기다릴 최대 시간(ms)입니다.
     *
     * AI 요청은 일반 API보다 오래 걸릴 수 있어
     * 현재 기본값은 30초로 설정합니다.
     */
    @Value("${ai.service.read-timeout:30000}")
    private long readTimeoutMillis;

    /*
     * Spring Boot가 FastAPI 서버를 호출할 때 사용할
     * 공통 RestClient 객체를 Bean으로 등록합니다.
     *
     * 현재 프로젝트에서는 RestClient.Builder Bean이
     * 자동 등록되어 있지 않으므로 RestClient.builder()를
     * 직접 사용합니다.
     */
    @Bean
    public RestClient aiServiceRestClient() {

        /*
         * JDK HttpClient에 FastAPI 서버 연결 timeout을 설정합니다.
         */
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(connectTimeoutMillis)
                )
                .build();

        /*
         * RestClient가 사용할 HTTP 요청 팩토리입니다.
         *
         * read timeout은 연결 후 FastAPI의 응답을
         * 기다릴 최대 시간을 의미합니다.
         */
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofMillis(readTimeoutMillis)
        );

        return RestClient.builder()
                .baseUrl(aiServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}