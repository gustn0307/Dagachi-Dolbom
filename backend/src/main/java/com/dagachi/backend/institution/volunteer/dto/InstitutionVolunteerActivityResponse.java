package com.dagachi.backend.institution.volunteer.dto;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.VisitResult;

import java.time.LocalDateTime;

/**
 * VOL-06 기관별 봉사자 활동 이력 응답.
 *
 * 다른 기관의 활동은 포함하지 않는다.
 */
public record InstitutionVolunteerActivityResponse(

        // 돌봄 활동 ID
        Long activityId,

        // 해당 활동의 돌봄 대상자 이름
        String recipientName,

        // 활동 예정 시각
        LocalDateTime scheduledAt,

        // 실제 활동 시작 시각
        LocalDateTime startedAt,

        // 실제 활동 완료 시각
        LocalDateTime completedAt,

        // 대상자를 만났는지에 대한 방문 결과
        VisitResult visitResult,

        // 기관의 활동 기록 검토 상태
        ActivityReviewStatus reviewStatus
) {
}