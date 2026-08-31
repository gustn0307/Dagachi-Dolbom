package com.dagachi.backend.common.ai.client;

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

}