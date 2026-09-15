package com.dagachi.backend.user.mypage.dto;

/**
 * STAT-01 내 활동 통계 응답 DTO.
 *
 * 마이페이지 상단 통계 카드(완료한 안부 확인 / 함께한 이웃)에서 사용한다.
 * 마일리지 관련 필드는 포함하지 않는다 (마일리지 기능 제외 확정).
 */
public record ActivityStatisticsResponse(

        // APPROVED 참여자로 완료(ActivityRecord APPROVED + MET)한 안부확인 횟수
        long completedCareCheckCount,

        // 위 조건을 만족하는 활동에서 만난 서로 다른 CareRecipient 수
        long careRecipientCount
) {
    public static ActivityStatisticsResponse of(
            long completedCareCheckCount,
            long careRecipientCount
    ) {
        return new ActivityStatisticsResponse(completedCareCheckCount, careRecipientCount);
    }
}