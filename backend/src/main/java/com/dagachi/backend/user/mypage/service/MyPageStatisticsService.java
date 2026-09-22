package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.dto.ActivityStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAT-01 내 활동 통계 조회 Service.
 *
 * userId는 반드시 로그인 principal에서만 받는다 (Request 파라미터 금지).
 *
 * [수정] REQ-AUTH-06: INSTITUTION/ADMIN 계정도 이 API를 호출하면 통계가
 * 조회되던 문제를 막기 위해, UserProfileService와 동일하게 USER Role만
 * 허용하도록 검증을 추가했다. 읽기 전용이라 우선순위는 낮았지만
 * REQ-AUTH-06이 "USER 전용 API 전체"를 명시하고 있어 함께 정리했다.
 */
@Service
@Transactional(readOnly = true)
public class MyPageStatisticsService {

    private final ActivityApplicationRepository activityApplicationRepository;
    private final UserRepository userRepository;

    public MyPageStatisticsService(
            ActivityApplicationRepository activityApplicationRepository,
            UserRepository userRepository
    ) {
        this.activityApplicationRepository = activityApplicationRepository;
        this.userRepository = userRepository;
    }

    public ActivityStatisticsResponse getMyActivityStatistics(Long userId) {
        // [수정] REQ-AUTH-06: USER Role만 본인 통계를 조회할 수 있다.
        validateUserRole(userId);

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

    /**
     * [신규] 삭제되지 않고, USER Role인 사용자만 통계 조회를 허용한다.
     */
    private void validateUserRole(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.USER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}