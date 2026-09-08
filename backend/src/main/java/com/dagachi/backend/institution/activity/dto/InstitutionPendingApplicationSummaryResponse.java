package com.dagachi.backend.institution.activity.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기관 활동의 승인 대기 신청 현황 응답.
 *
 * 사이드바에는 전체 승인 대기 신청 수를 표시하고,
 * 활동 관리 화면에는 돌봄 대상자별 대기 현황을 표시한다.
 */
public record InstitutionPendingApplicationSummaryResponse(

        // 기관 전체 승인 대기 신청자 수
        long totalPendingCount,

        // 승인 대기 신청이 존재하는 활동 목록
        List<PendingActivityResponse> activities
) {

    /**
     * 승인 대기 신청이 존재하는 활동 정보.
     */
    public record PendingActivityResponse(

            // 활동 번호
            Long activityId,

            // 돌봄 대상자 번호
            Long recipientId,

            // 돌봄 대상자 이름
            String recipientName,

            // 활동 예정 일시
            LocalDateTime scheduledAt,

            // 해당 활동의 승인 대기 신청자 수
            long pendingCount
    ) {
    }
}