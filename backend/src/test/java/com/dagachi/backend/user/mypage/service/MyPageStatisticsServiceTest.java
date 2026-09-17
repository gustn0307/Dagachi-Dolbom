package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.user.mypage.dto.ActivityStatisticsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * MyPageStatisticsService(STAT-01) 단위 테스트.
 *
 * 마일리지 관련 필드는 이 응답에 포함되지 않는다(마일리지 기능 제외 확정).
 */
@ExtendWith(MockitoExtension.class)
class MyPageStatisticsServiceTest {

    @Mock
    private ActivityApplicationRepository activityApplicationRepository;

    @InjectMocks
    private MyPageStatisticsService myPageStatisticsService;

    private static final Long USER_ID = 100L;

    @Test
    @DisplayName("STAT-01 내 활동 통계 - APPROVED+APPROVED(review)+MET 조건으로 집계한 값을 그대로 반환한다")
    void getMyActivityStatistics_완료한_안부확인과_대상자수를_반환한다() {
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
    @DisplayName("STAT-01 내 활동 통계 - 완료 이력이 없으면 0을 반환한다")
    void getMyActivityStatistics_이력이_없으면_0을_반환한다() {
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
}