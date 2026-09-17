package com.dagachi.backend.stats.service;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.stats.dto.PlatformSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformStatsService {

    private final UserRepository userRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final CareRecipientRepository careRecipientRepository;

    public PlatformStatsService(
            UserRepository userRepository,
            ActivityRecordRepository activityRecordRepository,
            CareRecipientRepository careRecipientRepository
    ) {
        this.userRepository = userRepository;
        this.activityRecordRepository = activityRecordRepository;
        this.careRecipientRepository = careRecipientRepository;
    }

    public PlatformSummaryResponse getPlatformSummary() {
        // 함께하는 시민: 관리자/기관담당자를 제외한 일반 회원 중 ACTIVE 상태
        long totalCitizens = userRepository.countByRoleAndStatusAndDeletedFalse(
                UserRole.USER,
                UserStatus.ACTIVE
        );

        // 누적 활동: 기관이 최종 승인(APPROVED)한 안부확인 방문 결과 건수
        long totalCompletedActivities = activityRecordRepository.countByReviewStatus(
                ActivityReviewStatus.APPROVED
        );

        // 도움이 필요한 이웃: 현재 관리 중인(ACTIVE) 돌봄 대상자 수
        long totalCareRecipients = careRecipientRepository.countByStatusAndDeletedFalse(
                CareRecipientStatus.ACTIVE
        );

        return new PlatformSummaryResponse(
                totalCitizens,
                totalCompletedActivities,
                totalCareRecipients
        );
    }
}