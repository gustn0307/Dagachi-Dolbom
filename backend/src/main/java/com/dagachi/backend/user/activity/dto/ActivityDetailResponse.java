package com.dagachi.backend.user.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACT-02 상세용 공개 활동 DTO. ActivityResponse + 본인 신청 상태.
 */
public record ActivityDetailResponse(
        Long activityId,
        String region,
        String ageGroup,
        String gender,
        LocalDateTime scheduledAt,
        Integer requiredPeople,
        Long approvedCount,
        String status,
        String genderCondition,
        BigDecimal distanceKm,
        String myApplicationStatus
) {

    public static ActivityDetailResponse of(
            ActivityResponse base,
            String myApplicationStatus
    ) {
        return new ActivityDetailResponse(
                base.activityId(),
                base.region(),
                base.ageGroup(),
                base.gender(),
                base.scheduledAt(),
                base.requiredPeople(),
                base.approvedCount(),
                base.status(),
                base.genderCondition(),
                base.distanceKm(),
                myApplicationStatus
        );
    }
}