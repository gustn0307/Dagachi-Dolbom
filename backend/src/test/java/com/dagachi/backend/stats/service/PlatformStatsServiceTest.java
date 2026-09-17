package com.dagachi.backend.stats.service;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.stats.dto.PlatformSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * PlatformStatsService(GET /api/stats/summary) 단위 테스트.
 *
 * 비회원도 접근 가능한 홈 화면 요약 통계이므로 로그인/권한 검사는 없다.
 * Service는 단순히 세 Repository의 count 결과를 그대로 조합해 반환한다.
 */
@ExtendWith(MockitoExtension.class)
class PlatformStatsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private CareRecipientRepository careRecipientRepository;

    @InjectMocks
    private PlatformStatsService platformStatsService;

    @Test
    @DisplayName("getPlatformSummary - 세 Repository의 집계값을 각각 올바른 조건으로 조회해 그대로 반환한다")
    void getPlatformSummary_각_집계값을_그대로_반환한다() {
        given(userRepository.countByRoleAndStatusAndDeletedFalse(UserRole.USER, UserStatus.ACTIVE))
                .willReturn(120L);
        given(activityRecordRepository.countByReviewStatus(ActivityReviewStatus.APPROVED))
                .willReturn(340L);
        given(careRecipientRepository.countByStatusAndDeletedFalse(CareRecipientStatus.ACTIVE))
                .willReturn(58L);

        PlatformSummaryResponse response = platformStatsService.getPlatformSummary();

        assertThat(response.totalCitizens()).isEqualTo(120L);
        assertThat(response.totalCompletedActivities()).isEqualTo(340L);
        assertThat(response.totalCareRecipients()).isEqualTo(58L);
    }

    @Test
    @DisplayName("getPlatformSummary - 데이터가 하나도 없으면 모두 0을 반환한다")
    void getPlatformSummary_데이터가_없으면_0을_반환한다() {
        given(userRepository.countByRoleAndStatusAndDeletedFalse(UserRole.USER, UserStatus.ACTIVE))
                .willReturn(0L);
        given(activityRecordRepository.countByReviewStatus(ActivityReviewStatus.APPROVED))
                .willReturn(0L);
        given(careRecipientRepository.countByStatusAndDeletedFalse(CareRecipientStatus.ACTIVE))
                .willReturn(0L);

        PlatformSummaryResponse response = platformStatsService.getPlatformSummary();

        assertThat(response.totalCitizens()).isZero();
        assertThat(response.totalCompletedActivities()).isZero();
        assertThat(response.totalCareRecipients()).isZero();
    }
}