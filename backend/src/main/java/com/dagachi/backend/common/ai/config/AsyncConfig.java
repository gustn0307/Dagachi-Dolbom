package com.dagachi.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 제보 AI 제목 생성 전용 비동기 스레드풀입니다.
 *
 * Tomcat 요청 스레드나 기존 embedding 후처리와 완전히 분리되어,
 * 동시 제보 접수가 몰려도 다른 API 요청 처리에 영향을 주지 않습니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiTitleTaskExecutor")
    public Executor aiTitleTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-title-");
        executor.initialize();
        return executor;
    }
}