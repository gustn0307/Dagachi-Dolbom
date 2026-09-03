package com.dagachi.backend.common.kakao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class KakaoLocalConfig {

    @Value("${kakao.local.base-url:https://dapi.kakao.com}")
    private String baseUrl;

    @Value("${kakao.local.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.local.connect-timeout:3000}")
    private long connectTimeoutMillis;

    @Value("${kakao.local.read-timeout:5000}")
    private long readTimeoutMillis;

    /**
     * Kakao Local API 호출에 사용하는 전용 RestClient입니다.
     *
     * AI Service용 RestClient와 base URL, 인증 헤더, timeout 정책이
     * 다르므로 별도 Bean으로 분리합니다.
     */
    @Bean
    public RestClient kakaoLocalRestClient() {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(connectTimeoutMillis)
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofMillis(readTimeoutMillis)
        );

        return RestClient.builder()
                .baseUrl(baseUrl)
                /*
                 * Kakao Local REST API는 모든 요청에
                 * "Authorization: KakaoAK {REST_API_KEY}"
                 * 헤더가 필요합니다.
                 */
                .defaultHeader(
                        "Authorization",
                        "KakaoAK " + restApiKey
                )
                .requestFactory(requestFactory)
                .build();
    }
}