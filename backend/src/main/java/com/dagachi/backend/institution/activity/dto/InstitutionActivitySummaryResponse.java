package com.dagachi.backend.institution.activity.dto;


import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.GenderCondition;

import java.time.LocalDateTime;

/**
 * ACT-04 기관 활동 목록에 표시할 요약 정보.
 *
 * 기관 담당자는 자신의 기관에 등록된 활동의
 * 대상자, 일정, 필요 인원, 승인 인원과 상태를 확인한다.
 */
public record InstitutionActivitySummaryResponse(
        // 돌봄 활동 ID
        Long activityId,
        // 돌봄 대상자 ID
        Long recipientId,
        // 돌봄 대상자 이름
        String recipientName,
        // 활동 예정 일시
        LocalDateTime scheduledAt,
        // 활동에 필요한 인원
        Integer requiredPeople,
        // 현재 승인된 참여 인원
        Long approvedCount,
        // 활동 참여 성별 조건
        GenderCondition genderCondition,
        // 활동 상태
        ActivityStatus status
) {
    /**
     * CareActivity Entity와 승인 인원수를
     * 기관 활동 목록 응답 DTO로 변환한다.
     */
    public static InstitutionActivitySummaryResponse of(
            CareActivity activity,
            long approvedCount
    ) {
        return new InstitutionActivitySummaryResponse(
                activity.getId(),
                activity.getRecipient().getId(),
                activity.getRecipient().getName(),
                activity.getScheduledAt(),
                activity.getRequiredPeople(),
                approvedCount,
                activity.getGenderCondition(),
                activity.getStatus()
        );
    }
}

