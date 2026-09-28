package com.dagachi.backend.user.application.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * REQ-ACT-17 자동배정 유효 후보 필터 전용 테스트.
 *
 * AI 재정렬 전에 Spring이 책임지는 하드 필터를 검증한다.
 *
 * 검증 대상:
 * - 정원이 이미 찬 활동 제외
 * - SAME_GENDER_ONE이 이미 충족된 활동
 * - 현재 사용자가 대상자와 같은 성별인 경우
 * - 다른 성별이어도 이후 자리가 남는 경우
 * - 다른 성별 사용자가 마지막 자리를 채워
 *   SAME_GENDER_ONE을 충족할 수 없게 되는 경우
 *
 * 기존 규칙 기반 자동배정과 AI 자동배정은
 * 동일한 유효 후보 필터를 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityApplicationAutoMatchEligibilityTest {

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
    private static final Long ACTIVITY_ID = 1L;

    // ---------------------------------------------------------------
    // 테스트 픽스처
    // ---------------------------------------------------------------

    private User buildUser(
            UserGender gender
    ) {
        User user = User.create(
                "auto-match@test.com",
                "encoded-pw",
                "자동배정 사용자",
                "자동배정",
                "010-0000-0000",
                gender
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                USER_ID
        );

        return user;
    }

    private CareActivity buildActivity(
            int requiredPeople,
            GenderCondition genderCondition
    ) {
        /*
         * 대상자는 FEMALE로 고정한다.
         *
         * 따라서 UserGender.FEMALE 사용자는 같은 성별,
         * UserGender.MALE 사용자는 다른 성별로 테스트할 수 있다.
         */
        CareRecipient recipient =
                CareRecipient.create(
                        null,
                        "테스트 대상자",
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
                10L
        );

        ReflectionTestUtils.setField(
                recipient,
                "lastCheckedAt",
                LocalDateTime.now()
                        .minusDays(10)
        );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        null,
                        null,
                        LocalDateTime.now()
                                .plusDays(1),
                        requiredPeople,
                        genderCondition
                );

        activity.changeStatus(
                ActivityStatus.RECRUITING
        );

        ReflectionTestUtils.setField(
                activity,
                "id",
                ACTIVITY_ID
        );

        return activity;
    }

    /**
     * REQ-ACT-17 유효 후보 필터에서
     * 모든 후보가 공통으로 사용하는 Repository 응답을 준비한다.
     *
     * applicantCount 조회는 후보가 실제로 살아남은 뒤에만 필요하므로
     * 여기서는 준비하지 않고 해당 테스트에서만 별도로 준비한다.
     */
    private void prepareEligibilityMocks(
            CareActivity activity,
            UserGender userGender,
            long approvedCount,
            long sameGenderApprovedCount
    ) {
        User user =
                buildUser(userGender);

        given(
                careActivityRepository
                        .findAutoMatchCandidates(
                                eq(USER_ID),
                                anyList()
                        )
        ).willReturn(
                List.of(activity)
        );

        given(
                userRepository.findById(USER_ID)
        ).willReturn(
                Optional.of(user)
        );

        /*
         * 후보 전체의 현재 APPROVED 인원 수.
         */
        given(
                activityApplicationRepository
                        .countApprovedMap(
                                anyList()
                        )
        ).willReturn(
                approvedCount == 0
                        ? Map.of()
                        : Map.of(
                        ACTIVITY_ID,
                        approvedCount
                )
        );

        /*
         * 후보 전체에서 대상자와 같은 성별의
         * APPROVED 참여자 수.
         */
        given(
                activityApplicationRepository
                        .countApprovedSameGenderMap(
                                anyList()
                        )
        ).willReturn(
                sameGenderApprovedCount == 0
                        ? Map.of()
                        : Map.of(
                        ACTIVITY_ID,
                        sameGenderApprovedCount
                )
        );
    }

    /**
     * 유효 후보가 실제 응답까지 만들어지는 테스트에서만
     * applicantCount 조회 결과를 준비한다.
     *
     * 필터에서 탈락하는 테스트에서는 이 Repository 호출 자체가
     * 발생하지 않으므로 공통 Mock으로 두지 않는다.
     */
    private void prepareApplicantCountMock() {
        given(
                activityApplicationRepository
                        .findActiveApplicationsByActivityIds(
                                List.of(ACTIVITY_ID)
                        )
        ).willReturn(
                List.of()
        );
    }

    // ---------------------------------------------------------------
    // REQ-ACT-17 : 정원 검증
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[REQ-ACT-17] 승인 인원이 필요 인원과 같으면 자동배정 후보에서 제외한다"
    )
    void fullCapacity_후보에서_제외한다() {

        CareActivity activity =
                buildActivity(
                        2,
                        GenderCondition.NONE
                );

        prepareEligibilityMocks(
                activity,
                UserGender.MALE,
                2L,
                0L
        );

        assertThatThrownBy(
                () ->
                        service.getAutoMatchCandidate(
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

    // ---------------------------------------------------------------
    // REQ-ACT-17 : SAME_GENDER_ONE
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[REQ-ACT-17] 기존 승인자 중 대상자와 같은 성별이 있으면 다른 성별 사용자도 마지막 자리 후보가 될 수 있다"
    )
    void sameGenderAlreadyApproved_다른성별도_후보가능() {

        CareActivity activity =
                buildActivity(
                        2,
                        GenderCondition.SAME_GENDER_ONE
                );

        /*
         * 여성 대상자 / 필요 2명
         * 현재 승인 1명
         * 기존 승인자가 여성
         * 현재 사용자는 남성
         *
         * 이미 SAME_GENDER_ONE 조건이 충족되어 있으므로
         * 남성 사용자가 마지막 자리를 채워도 된다.
         */
        prepareEligibilityMocks(
                activity,
                UserGender.MALE,
                1L,
                1L
        );

        prepareApplicantCountMock();

        var response =
                service.getAutoMatchCandidate(
                        USER_ID,
                        null,
                        null,
                        null
                );

        assertThat(
                response.activityId()
        ).isEqualTo(
                ACTIVITY_ID
        );
    }

    @Test
    @DisplayName(
            "[REQ-ACT-17] 현재 사용자가 대상자와 같은 성별이면 마지막 자리여도 후보가 된다"
    )
    void currentUserSameGender_마지막자리도_후보가능() {

        CareActivity activity =
                buildActivity(
                        2,
                        GenderCondition.SAME_GENDER_ONE
                );

        /*
         * 여성 대상자 / 필요 2명
         * 현재 승인 1명
         * 기존 동성 승인자는 없음
         * 현재 사용자는 여성
         *
         * 현재 사용자의 승인으로 SAME_GENDER_ONE을
         * 충족할 수 있으므로 후보가 된다.
         */
        prepareEligibilityMocks(
                activity,
                UserGender.FEMALE,
                1L,
                0L
        );

        prepareApplicantCountMock();

        var response =
                service.getAutoMatchCandidate(
                        USER_ID,
                        null,
                        null,
                        null
                );

        assertThat(
                response.activityId()
        ).isEqualTo(
                ACTIVITY_ID
        );
    }

    @Test
    @DisplayName(
            "[REQ-ACT-17] 현재 사용자가 다른 성별이어도 승인 후 자리가 남으면 후보가 된다"
    )
    void oppositeGender_추가자리가_남으면_후보가능() {

        CareActivity activity =
                buildActivity(
                        3,
                        GenderCondition.SAME_GENDER_ONE
                );

        /*
         * 여성 대상자 / 필요 3명
         * 현재 승인 1명
         * 기존 동성 승인자는 없음
         * 현재 사용자는 남성
         *
         * 남성 사용자가 승인되어도 한 자리가 더 남으므로,
         * 이후 여성 참여자가 승인되어 조건을 충족할 수 있다.
         */
        prepareEligibilityMocks(
                activity,
                UserGender.MALE,
                1L,
                0L
        );

        prepareApplicantCountMock();

        var response =
                service.getAutoMatchCandidate(
                        USER_ID,
                        null,
                        null,
                        null
                );

        assertThat(
                response.activityId()
        ).isEqualTo(
                ACTIVITY_ID
        );
    }

    @Test
    @DisplayName(
            "[REQ-ACT-17] 동성 승인자가 없고 다른 성별 사용자가 마지막 자리를 채우면 후보에서 제외한다"
    )
    void oppositeGender_마지막자리면_후보에서_제외한다() {

        CareActivity activity =
                buildActivity(
                        2,
                        GenderCondition.SAME_GENDER_ONE
                );

        /*
         * 여성 대상자 / 필요 2명
         * 현재 승인 1명
         * 기존 동성 승인자는 없음
         * 현재 사용자는 남성
         *
         * 남성 사용자가 마지막 자리를 채우면
         * 이후 SAME_GENDER_ONE을 충족할 방법이 없어지므로 제외한다.
         */
        prepareEligibilityMocks(
                activity,
                UserGender.MALE,
                1L,
                0L
        );

        assertThatThrownBy(
                () ->
                        service.getAutoMatchCandidate(
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

    // ---------------------------------------------------------------
    // AI 경로도 동일한 REQ-ACT-17 필터를 사용하는지 확인
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[REQ-ACT-17][REQ-AI-15] AI 자동배정도 유효하지 않은 마지막 자리 후보를 AI에 전달하지 않는다"
    )
    void aiMatching_유효하지않은_후보를_AI에_전달하지않는다() {

        CareActivity activity =
                buildActivity(
                        2,
                        GenderCondition.SAME_GENDER_ONE
                );

        prepareEligibilityMocks(
                activity,
                UserGender.MALE,
                1L,
                0L
        );

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

        /*
         * Spring 하드 필터에서 이미 제외됐으므로
         * FastAPI/OpenAI에는 잘못된 후보를 전달하지 않는다.
         */
        verifyNoInteractions(
                aiServiceClient
        );
    }
}