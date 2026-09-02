package com.dagachi.backend.common.ai.client;

import com.dagachi.backend.common.ai.dto.AiReportSummaryRequest;
import com.dagachi.backend.common.ai.dto.AiReportSummaryResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.StringUtils;

@Component
public class AiServiceClient {

    private final RestClient aiServiceRestClient;

    /*
     * AiServiceConfig에서 Bean으로 등록한 RestClient를
     * 생성자 주입받습니다.
     *
     * RestClient에는 FastAPI의 base URL과
     * connect/read timeout이 이미 공통 설정되어 있습니다.
     */
    public AiServiceClient(
            RestClient aiServiceRestClient
    ) {
        this.aiServiceRestClient = aiServiceRestClient;
    }


    /**
     * FastAPI의 제보 요약 API를 동기 방식으로 호출합니다.
     *
     * 호출 흐름:
     *
     * Spring Boot
     * -> POST /internal/ai/report-summary
     * -> FastAPI
     * -> OpenAI(gpt-4o-mini)
     * -> summary + model
     * -> Spring Boot
     *
     * Frontend에서는 FastAPI를 직접 호출하지 않고,
     * Spring Boot가 AI Service와의 내부 통신을 담당합니다.
     */
    public AiReportSummaryResponse summarizeReport(
            String content
    ) {

        AiReportSummaryRequest request =
                new AiReportSummaryRequest(content);

        /*
         * RestClient는 현재 Thread에서 FastAPI 응답이 돌아올 때까지
         * 기다리는 동기 방식으로 호출합니다.
         *
         * read timeout은 AiServiceConfig의 기본값 30초가 적용됩니다.
         */
        AiReportSummaryResponse response =
                aiServiceRestClient
                        .post()
                        .uri("/internal/ai/report-summary")
                        .body(request)
                        .retrieve()
                        .body(AiReportSummaryResponse.class);

        /*
         * HTTP 요청이 성공했더라도 응답 Body 자체가 없으면
         * 정상적인 AI 결과로 처리할 수 없습니다.
         */
        if (response == null) {
            throw new IllegalStateException(
                    "AI Service가 빈 응답을 반환했습니다."
            );
        }

        /*
         * FastAPI와의 응답 계약상 summary와 model은 모두 필수입니다.
         *
         * DB의 result_json 컬럼이 NOT NULL이어도
         * {"summary": null} 같은 JSON 자체는 저장될 수 있으므로,
         * 잘못된 AI 결과가 DB까지 전달되지 않도록 통신 경계에서 차단합니다.
         */
        if (!StringUtils.hasText(response.summary())
                || !StringUtils.hasText(response.model())) {
            throw new IllegalStateException(
                    "AI Service가 유효하지 않은 응답을 반환했습니다."
            );
        }

        return response;
    }
}