package com.dagachi.backend.common.ai.client;

import com.dagachi.backend.common.ai.dto.AiReportTitleRequest;
import com.dagachi.backend.common.ai.dto.AiReportTitleResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiServiceClient.generateReportTitle() 단위 테스트.
 *
 * ReportTitleGenerationServiceTest에서는 이 클라이언트를 통째로 mock 처리했기 때문에,
 * 이 클래스 자체의 통신 예외 -> CustomException 매핑 로직은 이 파일에서 검증한다.
 *
 * RestClient의 fluent API(post().uri().body().retrieve().body(Class))는 각 단계가
 * 인터페이스를 반환하므로, 체이닝 전체를 Mockito로 스텁한다.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceClientTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        aiServiceClient = new AiServiceClient(restClient);

        // RestClient.post().uri(...).body(...).retrieve() 체이닝 전체를 연결한다.
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/internal/ai/report-title")).thenReturn(requestBodySpec);
        // body(Object)가 제네릭/오버로드된 메서드라 타입 없는 any()는 Mockito가
        // "아무 값이나"가 아니라 "null만"으로 잘못 등록하는 경우가 있어 타입을 명시한다.
        when(requestBodySpec.body(any(AiReportTitleRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("generateReportTitle - 정상 응답이면 title/model을 그대로 반환한다")
    void generateReportTitle_정상응답이면_그대로_반환한다() {
        AiReportTitleResponse expected = new AiReportTitleResponse("독거노인 안전 확인 요청", "gpt-4o-mini");
        given(responseSpec.body(AiReportTitleResponse.class)).willReturn(expected);

        AiReportTitleResponse response = aiServiceClient.generateReportTitle("제보 원문");

        assertThat(response.title()).isEqualTo("독거노인 안전 확인 요청");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("generateReportTitle - 응답 Body 자체가 없으면 AI_SERVICE_INVALID_RESPONSE")
    void generateReportTitle_응답이_없으면_예외를_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class)).willReturn(null);

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("generateReportTitle - title이 빈 값이면 AI_SERVICE_INVALID_RESPONSE")
    void generateReportTitle_title이_비어있으면_예외를_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class))
                .willReturn(new AiReportTitleResponse("", "gpt-4o-mini"));

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("generateReportTitle - model이 빈 값이면 AI_SERVICE_INVALID_RESPONSE")
    void generateReportTitle_model이_비어있으면_예외를_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class))
                .willReturn(new AiReportTitleResponse("제목", "  "));

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("generateReportTitle - 연결 타임아웃(메시지에 timed out 포함)이면 AI_SERVICE_TIMEOUT")
    void generateReportTitle_타임아웃이면_AI_SERVICE_TIMEOUT을_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class))
                .willThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("generateReportTitle - 타임아웃이 아닌 연결 실패면 AI_SERVICE_UNAVAILABLE")
    void generateReportTitle_연결실패면_AI_SERVICE_UNAVAILABLE을_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class))
                .willThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("generateReportTitle - FastAPI가 4xx/5xx를 반환하면 AI_SERVICE_UNAVAILABLE")
    void generateReportTitle_FastAPI가_에러응답을_반환하면_AI_SERVICE_UNAVAILABLE을_던진다() {
        RestClientResponseException exception = mock(RestClientResponseException.class);
        given(responseSpec.body(AiReportTitleResponse.class)).willThrow(exception);

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("generateReportTitle - 그 외 RestClientException은 AI_SERVICE_INVALID_RESPONSE")
    void generateReportTitle_그외_RestClientException은_AI_SERVICE_INVALID_RESPONSE를_던진다() {
        given(responseSpec.body(AiReportTitleResponse.class))
                .willThrow(new RestClientException("역직렬화 실패"));

        assertThatThrownBy(() -> aiServiceClient.generateReportTitle("제보 원문"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SERVICE_INVALID_RESPONSE);
    }
}