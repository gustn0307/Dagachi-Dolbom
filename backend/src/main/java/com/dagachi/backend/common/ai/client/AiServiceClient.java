package com.dagachi.backend.common.ai.client;

import com.dagachi.backend.common.ai.dto.AiReportSummaryRequest;
import com.dagachi.backend.common.ai.dto.AiReportSummaryResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
            @Qualifier("aiServiceRestClient")
            RestClient aiServiceRestClient
    ) {
        this.aiServiceRestClient = aiServiceRestClient;
    }


    /**
     * FastAPI의 제보 요약 API를 동기 방식으로 호출합니다.
     * <p>
     * 호출 흐름:
     * <p>
     * Spring Boot
     * -> POST /internal/ai/report-summary
     * -> FastAPI
     * -> OpenAI(gpt-4o-mini)
     * -> summary + model
     * -> Spring Boot
     * <p>
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
        AiReportSummaryResponse response;

        try {
            response =
                    aiServiceRestClient
                            .post()
                            .uri("/internal/ai/report-summary")
                            .body(request)
                            .retrieve()
                            .body(AiReportSummaryResponse.class);

        } catch (ResourceAccessException exception) {

            /*
             * 연결 실패와 read timeout은 모두 ResourceAccessException 계열로
             * 전달될 수 있습니다.
             *
             * timeout 여부를 더 세밀하게 구분하려면 원인 예외를 분석해야 하지만,
             * 현재 MVP에서는 메시지 기준으로 timeout을 별도 처리합니다.
             */
            String message = exception.getMessage();

            if (message != null
                    && message.toLowerCase().contains("timed out")) {
                throw new CustomException(
                        ErrorCode.AI_SERVICE_TIMEOUT
                );
            }

            throw new CustomException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE
            );

        } catch (RestClientResponseException exception) {

            /*
             * FastAPI가 4xx/5xx 응답을 반환한 경우입니다.
             *
             * FastAPI 내부에서는 OpenAI 인증/외부 API 장애 등을
             * 502/503으로 변환하므로 Spring API에서는
             * 외부 AI 서비스 이용 불가 상태로 통일합니다.
             */
            throw new CustomException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE
            );
        } catch (RestClientException exception) {
            throw new CustomException(
                    ErrorCode.AI_SERVICE_INVALID_RESPONSE
            );
        }

        /*
         * HTTP 요청이 성공했더라도 응답 Body 자체가 없으면
         * 정상적인 AI 결과로 처리할 수 없습니다.
         */
        if (response == null) {
            throw new CustomException(
                    ErrorCode.AI_SERVICE_INVALID_RESPONSE
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
            throw new CustomException(
                    ErrorCode.AI_SERVICE_INVALID_RESPONSE
            );
        }

        return response;
    }
}