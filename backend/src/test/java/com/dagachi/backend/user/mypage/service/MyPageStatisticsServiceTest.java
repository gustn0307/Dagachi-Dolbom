package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.dto.ActivityStatisticsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MyPageStatisticsService(STAT-01) 단위 테스트.
 *
 * 마일리지 관련 필드는 이 응답에 포함되지 않는다(마일리지 기능 제외 확정).
 *
 * [수정 - 유지훈] REQ-AUTH-06: INSTITUTION/ADMIN 계정이 이 API를 호출할 수
 * 있던 문제를 막기 위해 Service에 Role 검증(UserRepository 의존성)을
 * 추가했다. 원본 2개 테스트는 이제 findByIdAndDeletedFalse가 먼저
 * 호출되므로 userRepository mock/stub을 보강했고, assertion 내용 자체는
 * 원본 그대로 유지했다. Role 검증 회귀 테스트 3개를 새로 추가했다.
 */
@ExtendWith(MockitoExtension.class)
class MyPageStatisticsServiceTest {

    @Mock
    private ActivityApplicationRepository activityApplicationRepository;

    // [신규] Role 검증 추가로 새로 생긴 의존성.
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MyPageStatisticsService myPageStatisticsService;

    private static final Long USER_ID = 100L;

    // [신규] 정상 케이스 stub용 활성 USER 계정 헬퍼.
    private User buildUser(UserRole role) {
        User user = User.create(
                "user@test.com", "encoded-pw", "홍길동", "길동이", "010-1234-5678", UserGender.MALE
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Test
    @DisplayName("REQ-DASH-03 - STAT-01 내 활동 통계 - APPROVED+APPROVED(review)+MET 조건으로 집계한 값을 그대로 반환한다")
    void getMyActivityStatistics_완료한_안부확인과_대상자수를_반환한다() {
        // [수정] Role 검증이 추가되어 userRepository stub이 필요해졌다.
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(buildUser(UserRole.USER)));

        given(activityApplicationRepository.countCompletedCareChecks(
                eq(USER_ID), eq(ApplicationStatus.APPROVED), eq(ActivityReviewStatus.APPROVED), eq(VisitResult.MET)
        )).willReturn(7L);
        given(activityApplicationRepository.countDistinctCareRecipients(
                eq(USER_ID), eq(ApplicationStatus.APPROVED), eq(ActivityReviewStatus.APPROVED), eq(VisitResult.MET)
        )).willReturn(5L);

        ActivityStatisticsResponse response = myPageStatisticsService.getMyActivityStatistics(USER_ID);

        assertThat(response.completedCareCheckCount()).isEqualTo(7L);
        assertThat(response.careRecipientCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("REQ-DASH-03 - STAT-01 내 활동 통계 - 완료 이력이 없으면 0을 반환한다")
    void getMyActivityStatistics_이력이_없으면_0을_반환한다() {
        // [수정] Role 검증이 추가되어 userRepository stub이 필요해졌다.
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(buildUser(UserRole.USER)));

        given(activityApplicationRepository.countCompletedCareChecks(
                eq(USER_ID), eq(ApplicationStatus.APPROVED), eq(ActivityReviewStatus.APPROVED), eq(VisitResult.MET)
        )).willReturn(0L);
        given(activityApplicationRepository.countDistinctCareRecipients(
                eq(USER_ID), eq(ApplicationStatus.APPROVED), eq(ActivityReviewStatus.APPROVED), eq(VisitResult.MET)
        )).willReturn(0L);

        ActivityStatisticsResponse response = myPageStatisticsService.getMyActivityStatistics(USER_ID);

        assertThat(response.completedCareCheckCount()).isZero();
        assertThat(response.careRecipientCount()).isZero();
    }

    // ---------------------------------------------------------------
    // [신규 - 유지훈] REQ-AUTH-06 Role 검증 회귀 테스트
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-AUTH-06 - STAT-01 내 활동 통계 - INSTITUTION 계정이면 FORBIDDEN")
    void getMyActivityStatistics_INSTITUTION계정이면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(buildUser(UserRole.INSTITUTION)));

        assertThatThrownBy(() -> myPageStatisticsService.getMyActivityStatistics(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        // Role 검증에서 막혔으므로 통계 집계 쿼리는 호출되지 않아야 한다.
        verify(activityApplicationRepository, never()).countCompletedCareChecks(
                any(), any(), any(), any()
        );
        verify(activityApplicationRepository, never()).countDistinctCareRecipients(
                any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("REQ-AUTH-06 - STAT-01 내 활동 통계 - ADMIN 계정이면 FORBIDDEN")
    void getMyActivityStatistics_ADMIN계정이면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(buildUser(UserRole.ADMIN)));

        assertThatThrownBy(() -> myPageStatisticsService.getMyActivityStatistics(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("STAT-01 내 활동 통계 - 탈퇴/삭제된 계정이면 USER_NOT_FOUND")
    void getMyActivityStatistics_탈퇴계정이면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> myPageStatisticsService.getMyActivityStatistics(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}