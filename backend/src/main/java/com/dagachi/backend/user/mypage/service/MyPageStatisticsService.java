package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.user.mypage.dto.ActivityStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAT-01 내 활동 통계 조회 Service.
 *
 * userId는 반드시 로그인 principal에서만 받는다 (Request 파라미터 금지).
 */
@Service
@Transactional(readOnly = true)
public class MyPageStatisticsService {

    private final ActivityApplicationRepository activityApplicationRepository;

    public MyPageStatisticsService(ActivityApplicationRepository activityApplicationRepository) {
        this.activityApplicationRepository = activityApplicationRepository;
    }

    public ActivityStatisticsResponse getMyActivityStatistics(Long userId) {
        long completedCareCheckCount = activityApplicationRepository.countCompletedCareChecks(
                userId,
                ApplicationStatus.APPROVED,
                ActivityReviewStatus.APPROVED,
                VisitResult.MET
        );

        long careRecipientCount = activityApplicationRepository.countDistinctCareRecipients(
                userId,
                ApplicationStatus.APPROVED,
                ActivityReviewStatus.APPROVED,
                VisitResult.MET
        );

        return ActivityStatisticsResponse.of(completedCareCheckCount, careRecipientCount);
    }
}