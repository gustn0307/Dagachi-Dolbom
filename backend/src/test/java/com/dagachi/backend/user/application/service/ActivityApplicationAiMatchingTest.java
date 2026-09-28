package com.dagachi.backend.user.application.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiActivityMatchingRequest;
import com.dagachi.backend.common.ai.dto.AiActivityMatchingResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.application.dto.AutoMatchCandidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * APP-02 AI 자동배정 전용 단위 테스트.
 *
 * 기존 ActivityApplicationServiceTest는 수정하지 않고,
 * AI 후보 선별 / AI 재정렬 / fallback 동작만 별도로 검증한다.
 *
 * REQ-AI-15 : 규칙 기반 유효 후보 집합 안에서 AI가 우선순위를 재정렬
 * REQ-AI-16 : AI 매칭 결과의 우선순위와 사용자 이해 가능한 추천 근거
 * REQ-AI-17 : AI 장애 시 기존 규칙 기반 fallback
 */
@ExtendWith(MockitoExtension.class)
class ActivityApplicationAiMatchingTest {

    @Mock
    private ActivityApplicationRepository activityApplicationRepository;

    @Mock
    private CareActivityRepository careActivityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @Mock
    private ChecklistResponseRepository checklistResponseRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private ActivityApplicationService service;

    private static final Long USER_ID = 100L;

    // ---------------------------------------------------------------
    // 테스트 픽스처
    // ---------------------------------------------------------------

    private CareRecipient buildRecipient(
            long recipientId,
            LocalDateTime lastCheckedAt
    ) {
        CareRecipient recipient = CareRecipient.create(
                null,
                "테스트 대상자" + recipientId,
                UserGender.FEMALE,
                1950,
                "010-1111-2222",
                "서울시 강남구 역삼동 123",
                null,
                null,
                null,
                ConsentStatus.AGREED
        );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                recipientId
        );

        ReflectionTestUtils.setField(
                recipient,
                "lastCheckedAt",
                lastCheckedAt
        );

        return recipient;
    }

    private CareActivity buildActivity(
            long activityId,
            long recipientId,
            LocalDateTime lastCheckedAt
    ) {
        CareRecipient recipient =
                buildRecipient(
                        recipientId,
                        lastCheckedAt
                );

        CareActivity activity = CareActivity.create(
                recipient,
                null,
                null,
                LocalDateTime.now().plusDays(1),
                2,
                GenderCondition.NONE
        );

        activity.changeStatus(
                ActivityStatus.RECRUITING
        );

        ReflectionTestUtils.setField(
                activity,
                "id",
                activityId
        );

        return activity;
    }

    /**
     * AI 매칭 테스트에서 공통으로 필요한 Repository 응답을 준비한다.
     *
     * REQ-ACT-17 유효 후보 필터에 필요한 사용자 성별,
     * 현재 승인 인원, 같은 성별 승인 인원도 함께 준비한다.
     *
     * 사용자 완료 경험과 대상자의 최근 승인 기록은 없는 상태로 두어,
     * 테스트가 AI 순위 / 후보 선별 / fallback 자체에 집중하도록 한다.
     */
    private void prepareCommonMocks(
            List<CareActivity> candidates
    ) {
        given(
                careActivityRepository.findAutoMatchCandidates(
                        eq(USER_ID),
                        anyList()
                )
        ).willReturn(candidates);

        /*
         * REQ-ACT-17 유효 후보 필터에서
         * 현재 사용자의 성별을 확인한다.
         *
         * 현재 테스트 활동은 GenderCondition.NONE이므로
         * 사용자의 성별 자체가 후보 탈락에는 영향을 주지 않는다.
         */
        User user = User.create(
                "matching@test.com",
                "encoded-pw",
                "매칭사용자",
                "매칭사용자",
                "010-0000-0000",
                UserGender.MALE
        );

        given(
                userRepository.findById(USER_ID)
        ).willReturn(
                Optional.of(user)
        );

        /*
         * REQ-ACT-17 유효 후보 필터에서
         * 후보 전체의 현재 APPROVED 인원을 배치 조회한다.
         *
         * 현재 테스트에서는 승인자가 없는 상태로 둔다.
         */
        given(
                activityApplicationRepository
                        .countApprovedMap(
                                anyList()
                        )
        ).willReturn(
                Map.of()
        );

        /*
         * REQ-ACT-17 SAME_GENDER_ONE 검증용 배치 조회.
         *
         * 현재 테스트 활동은 GenderCondition.NONE이므로
         * 같은 성별 승인자도 없는 상태로 둔다.
         */
        given(
                activityApplicationRepository
                        .countApprovedSameGenderMap(
                                anyList()
                        )
        ).willReturn(
                Map.of()
        );

        given(
                activityApplicationRepository.countCompletedCareChecks(
                        USER_ID,
                        ApplicationStatus.APPROVED,
                        ActivityReviewStatus.APPROVED,
                        VisitResult.MET
                )
        ).willReturn(0L);

        given(
                activityRecordRepository
                        .findApprovedMetRecordsByUserId(
                                USER_ID
                        )
        ).willReturn(List.of());

        given(
                activityRecordRepository
                        .findApprovedRecordsByRecipientIds(
                                anyList()
                        )
        ).willReturn(List.of());

        given(
                activityApplicationRepository
                        .findActiveApplicationsByActivityIds(
                                anyList()
                        )
        ).willReturn(List.of());
    }

    // ---------------------------------------------------------------
    // REQ-AI-15 / REQ-AI-16 : 정상 AI 매칭
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[REQ-AI-15][REQ-AI-16] AI가 기존 규칙 후보를 재정렬하고 rank/reason/model을 포함한다"
    )
    void getAiAutoMatchCandidates_AI순위대로_정렬하고_추천정보를_반환한다() {

        // given
        CareActivity activity1 =
                buildActivity(
                        1L,
                        101L,
                        LocalDateTime.now().minusDays(30)
                );

        CareActivity activity2 =
                buildActivity(
                        2L,
                        102L,
                        LocalDateTime.now().minusDays(5)
                );

        prepareCommonMocks(
                List.of(
                        activity1,
                        activity2
                )
        );

        AiActivityMatchingResponse aiResponse =
                new AiActivityMatchingResponse(
                        List.of(
                                new AiActivityMatchingResponse.Recommendation(
                                        2L,
                                        1,
                                        "두 번째 활동을 우선 추천합니다."
                                ),
                                new AiActivityMatchingResponse.Recommendation(
                                        1L,
                                        2,
                                        "첫 번째 활동을 다음으로 추천합니다."
                                )
                        ),
                        "gpt-4o-mini"
                );

        given(
                aiServiceClient.matchActivities(
                        any(
                                AiActivityMatchingRequest.class
                        )
                )
        ).willReturn(aiResponse);

        // when
        List<AutoMatchCandidateResponse> responses =
                service.getAiAutoMatchCandidates(
                        USER_ID,
                        null,
                        null,
                        null
                );

        // then
        assertThat(responses)
                .hasSize(2);

        assertThat(
                responses.get(0).activityId()
        ).isEqualTo(2L);

        assertThat(
                responses.get(0).reason()
        ).isEqualTo(
                "두 번째 활동을 우선 추천합니다."
        );

        assertThat(
                responses.get(0).model()
        ).isEqualTo(
                "gpt-4o-mini"
        );

        assertThat(
                responses.get(1).activityId()
        ).isEqualTo(1L);

        assertThat(
                responses.get(1).reason()
        ).isEqualTo(
                "첫 번째 활동을 다음으로 추천합니다."
        );

        assertThat(
                responses.get(1).model()
        ).isEqualTo(
                "gpt-4o-mini"
        );
    }

    @Test
    @DisplayName(
            "AI 활동 매칭 - 기존 규칙 후보 중 최대 10건까지만 AI에 전달한다"
    )
    void getAiAutoMatchCandidates_AI요청후보는_최대10건이다() {

        // given
        List<CareActivity> candidates =
                new ArrayList<>();

        // 모든 후보의 안부확인 시각을 같게 만들어
        // 거리도 없고 안부순위도 같은 완전 동점 상태로 만든다.
        LocalDateTime sameLastCheckedAt =
                LocalDateTime.now().minusDays(10);

        for (long id = 1L; id <= 12L; id++) {
            candidates.add(
                    buildActivity(
                            id,
                            100L + id,
                            sameLastCheckedAt
                    )
            );
        }

        prepareCommonMocks(candidates);

        List<AiActivityMatchingResponse.Recommendation>
                recommendations =
                new ArrayList<>();

        for (int rank = 1; rank <= 10; rank++) {
            recommendations.add(
                    new AiActivityMatchingResponse.Recommendation(
                            (long) rank,
                            rank,
                            "추천 이유 " + rank
                    )
            );
        }

        given(
                aiServiceClient.matchActivities(
                        any(
                                AiActivityMatchingRequest.class
                        )
                )
        ).willReturn(
                new AiActivityMatchingResponse(
                        recommendations,
                        "gpt-4o-mini"
                )
        );

        ArgumentCaptor<AiActivityMatchingRequest>
                requestCaptor =
                ArgumentCaptor.forClass(
                        AiActivityMatchingRequest.class
                );

        // when
        List<AutoMatchCandidateResponse> responses =
                service.getAiAutoMatchCandidates(
                        USER_ID,
                        null,
                        null,
                        null
                );

        // then
        verify(
                aiServiceClient
        ).matchActivities(
                requestCaptor.capture()
        );

        AiActivityMatchingRequest capturedRequest =
                requestCaptor.getValue();

        // AI에는 최대 10건만 전달되어야 한다.
        assertThat(
                capturedRequest.candidates()
        ).hasSize(10);

        assertThat(
                responses
        ).hasSize(10);

        // 거리와 안부순위가 모두 같은 경우
        // activityId 오름차순으로 10건을 선택한다.
        assertThat(
                capturedRequest.candidates()
                        .stream()
                        .map(
                                AiActivityMatchingRequest
                                        .Candidate
                                        ::activityId
                        )
                        .toList()
        ).containsExactly(
                1L,
                2L,
                3L,
                4L,
                5L,
                6L,
                7L,
                8L,
                9L,
                10L
        );
    }

    // ---------------------------------------------------------------
    // REQ-AI-17 : AI 장애 fallback
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[REQ-AI-17] AI Service 장애 시 기존 규칙 기반 순서로 fallback한다"
    )
    void getAiAutoMatchCandidates_AI장애시_기존순서로_fallback한다() {

        // given
        CareActivity oldActivity =
                buildActivity(
                        1L,
                        101L,
                        LocalDateTime.now().minusDays(30)
                );

        CareActivity recentActivity =
                buildActivity(
                        2L,
                        102L,
                        LocalDateTime.now().minusDays(2)
                );

        prepareCommonMocks(
                List.of(
                        oldActivity,
                        recentActivity
                )
        );

        given(
                aiServiceClient.matchActivities(
                        any(
                                AiActivityMatchingRequest.class
                        )
                )
        ).willThrow(
                new CustomException(
                        ErrorCode.AI_SERVICE_UNAVAILABLE
                )
        );

        // when
        List<AutoMatchCandidateResponse> responses =
                service.getAiAutoMatchCandidates(
                        USER_ID,
                        null,
                        null,
                        null
                );

        // then
        assertThat(
                responses
        ).hasSize(2);

        // 위치 정보가 없으므로
        // 안부확인이 더 오래된 활동이 먼저 나온다.
        assertThat(
                responses.get(0).activityId()
        ).isEqualTo(1L);

        assertThat(
                responses.get(1).activityId()
        ).isEqualTo(2L);

        // fallback에서는 AI 추천 정보가 없어야 한다.
        assertThat(responses)
                .allSatisfy(
                        response -> {
                            assertThat(
                                    response.reason()
                            ).isNull();

                            assertThat(
                                    response.model()
                            ).isNull();
                        }
                );
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] AI timeout 발생 시 추천 기능을 실패시키지 않고 fallback한다"
    )
    void getAiAutoMatchCandidates_AI_timeout시_fallback한다() {

        // given
        CareActivity activity =
                buildActivity(
                        1L,
                        101L,
                        LocalDateTime.now().minusDays(10)
                );

        prepareCommonMocks(
                List.of(activity)
        );

        given(
                aiServiceClient.matchActivities(
                        any(
                                AiActivityMatchingRequest.class
                        )
                )
        ).willThrow(
                new CustomException(
                        ErrorCode.AI_SERVICE_TIMEOUT
                )
        );

        // when
        List<AutoMatchCandidateResponse> responses =
                service.getAiAutoMatchCandidates(
                        USER_ID,
                        null,
                        null,
                        null
                );

        // then
        assertThat(
                responses
        ).hasSize(1);

        assertThat(
                responses.get(0).activityId()
        ).isEqualTo(1L);

        assertThat(
                responses.get(0).reason()
        ).isNull();

        assertThat(
                responses.get(0).model()
        ).isNull();
    }

    @Test
    @DisplayName(
            "[REQ-AI-17] AI 응답 형식이 잘못된 경우에도 fallback한다"
    )
    void getAiAutoMatchCandidates_AI응답오류시_fallback한다() {

        // given
        CareActivity activity =
                buildActivity(
                        1L,
                        101L,
                        LocalDateTime.now().minusDays(10)
                );

        prepareCommonMocks(
                List.of(activity)
        );

        given(
                aiServiceClient.matchActivities(
                        any(
                                AiActivityMatchingRequest.class
                        )
                )
        ).willThrow(
                new CustomException(
                        ErrorCode.AI_SERVICE_INVALID_RESPONSE
                )
        );

        // when
        List<AutoMatchCandidateResponse> responses =
                service.getAiAutoMatchCandidates(
                        USER_ID,
                        null,
                        null,
                        null
                );

        // then
        assertThat(
                responses
        ).hasSize(1);

        assertThat(
                responses.get(0).activityId()
        ).isEqualTo(1L);

        assertThat(
                responses.get(0).reason()
        ).isNull();

        assertThat(
                responses.get(0).model()
        ).isNull();
    }

    // ---------------------------------------------------------------
    // APP-02 기본 후보 조건
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "APP-02 AI 자동배정 - 후보가 없으면 NO_AUTO_MATCH_CANDIDATE"
    )
    void getAiAutoMatchCandidates_후보가_없으면_예외를_던진다() {

        // given
        given(
                careActivityRepository
                        .findAutoMatchCandidates(
                                eq(USER_ID),
                                anyList()
                        )
        ).willReturn(
                List.of()
        );

        // when & then
        assertThatThrownBy(
                () ->
                        service.getAiAutoMatchCandidates(
                                USER_ID,
                                null,
                                null,
                                null
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.NO_AUTO_MATCH_CANDIDATE
                );
    }
}