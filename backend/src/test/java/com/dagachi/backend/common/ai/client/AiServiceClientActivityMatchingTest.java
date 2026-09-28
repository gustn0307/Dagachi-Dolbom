package com.dagachi.backend.common.ai.client;

import com.dagachi.backend.common.ai.dto.AiActivityMatchingRequest;
import com.dagachi.backend.common.ai.dto.AiActivityMatchingResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spring -> FastAPI 활동 AI 매칭 통신 경계 단위 테스트.
 *
 * 실제 FastAPI를 호출하지 않고 RestClient를 Mock 처리한다.
 *
 * REQ-AI-15 : AI 매칭 결과 반환
 * REQ-AI-16 : 추천 이유 반환
 * REQ-AI-17 : AI 장애/timeout/잘못된 응답 fallback 기반 오류 매핑
 */
@ExtendWith(MockitoExtension.class)
class AiServiceClientActivityMatchingTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private AiServiceClient aiServiceClient;

    private AiActivityMatchingRequest request;

    @BeforeEach
    void setUp() {
        aiServiceClient = new AiServiceClient(restClient);

        /*
         * 이 테스트는 요청 내용 자체가 아니라
         * Spring -> FastAPI 통신과 응답 검증을 확인한다.
         *
         * 따라서 Profile/Candidate 상세 데이터는 비워 두고
         * 실제 DTO 객체만 사용한다.
         */
        request = new AiActivityMatchingRequest(
                null,
                List.of()
        );

        when(restClient.post())
                .thenReturn(requestBodyUriSpec);

        when(
                requestBodyUriSpec.uri(
                        "/internal/ai/activity-matching"
                )
        ).thenReturn(requestBodySpec);

        when(
                requestBodySpec.body(
                        any(AiActivityMatchingRequest.class)
                )
        ).thenReturn(requestBodySpec);

        when(requestBodySpec.retrieve())
                .thenReturn(responseSpec);
    }

    @Test
    @DisplayName(
            "[REQ-AI-15][REQ-AI-16] 정상 응답이면 추천 순위/이유/model을 그대로 반환한다"
    )
    void matchActivities_정상응답이면_그대로_반환한다() {

        AiActivityMatchingResponse expected =
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        2L,
                                        1,
                                        "최근 안부 확인 경과를 고려해 우선 추천합니다."
                                ),
                                new AiActivityMatchingResponse.Recommendation(
                                        1L,
                                        2,
                                        "거리와 활동 경험을 고려해 추천합니다."
                                )
                        ),
                        "gpt-4o-mini"
                );

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(expected);

        AiActivityMatchingResponse response =
                aiServiceClient.matchActivities(request);

        assertThat(response)
                .isSameAs(expected);

        assertThat(response.recommendations())
                .hasSize(2);

        assertThat(
                response.recommendations()
                        .get(0)
                        .activityId()
        ).isEqualTo(2L);

        assertThat(
                response.recommendations()
                        .get(0)
                        .rank()
        ).isEqualTo(1);

        assertThat(
                response.recommendations()
                        .get(0)
                        .reason()
        ).isEqualTo(
                "최근 안부 확인 경과를 고려해 우선 추천합니다."
        );

        assertThat(response.model())
                .isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] 응답 Body가 없으면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_응답Body가_없으면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(null);

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] recommendations가 비어 있으면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_추천목록이_비어있으면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        List.of(),
                        "gpt-4o-mini"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] model이 비어 있으면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_model이_비어있으면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        1L,
                                        1,
                                        "추천 이유"
                                )
                        ),
                        " "
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] recommendation activityId가 없으면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_activityId가_없으면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        null,
                                        1,
                                        "추천 이유"
                                )
                        ),
                        "gpt-4o-mini"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] recommendation rank가 1 미만이면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_rank가_잘못되면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        1L,
                                        0,
                                        "추천 이유"
                                )
                        ),
                        "gpt-4o-mini"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-16][REQ-AI-17] 추천 이유가 비어 있으면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_추천이유가_비어있으면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        1L,
                                        1,
                                        " "
                                )
                        ),
                        "gpt-4o-mini"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] FastAPI read timeout이면 AI_SERVICE_TIMEOUT"
    )
    void matchActivities_timeout이면_AI_SERVICE_TIMEOUT() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willThrow(
                new ResourceAccessException(
                        "Read timed out"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_TIMEOUT
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] FastAPI 연결 실패면 AI_SERVICE_UNAVAILABLE"
    )
    void matchActivities_연결실패면_AI_SERVICE_UNAVAILABLE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willThrow(
                new ResourceAccessException(
                        "Connection refused"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_UNAVAILABLE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] FastAPI가 4xx/5xx를 반환하면 AI_SERVICE_UNAVAILABLE"
    )
    void matchActivities_FastAPI에러응답이면_AI_SERVICE_UNAVAILABLE() {

        RestClientResponseException exception =
                mock(
                        RestClientResponseException.class
                );

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willThrow(exception);

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_UNAVAILABLE
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] 응답 역직렬화 등 RestClient 오류면 AI_SERVICE_INVALID_RESPONSE"
    )
    void matchActivities_RestClient오류면_INVALID_RESPONSE() {

        given(
                responseSpec.body(
                        AiActivityMatchingResponse.class
                )
        ).willThrow(
                new RestClientException(
                        "역직렬화 실패"
                )
        );

        assertThatThrownBy(
                () -> aiServiceClient.matchActivities(request)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                );
    }
}