package com.dagachi.backend.user.application.dto;

import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.common.util.GeoUtils;
import com.dagachi.backend.domain.entity.CareActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * APP-02 자동배정 후보 응답입니다.
 *
 * 기존 ACT-01 ActivityResponse는 수정하지 않고,
 * 자동배정 화면에서 실제 사용하는 정보와
 * AI 추천 이유만 별도로 전달합니다.
 */
public record AutoMatchCandidateResponse(
        Long activityId,
        String region,
        String ageGroup,
        String gender,
        LocalDateTime scheduledAt,
        Integer requiredPeople,
        Long approvedCount,
        Long applicantCount,
        BigDecimal distanceKm,
        String reason,
        String model
) {

    /**
     * 추천할 CareActivity를 화면 응답 형태로 변환합니다.
     *
     * AI를 사용하지 않은 fallback 추천이면
     * reason과 model에는 null을 전달합니다.
     */
    public static AutoMatchCandidateResponse of(
            CareActivity activity,
            long approvedCount,
            long applicantCount,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            String reason,
            String model
    ) {
        var recipient = activity.getRecipient();

        BigDecimal distanceKm = GeoUtils.calculateDistanceKm(
                userLatitude,
                userLongitude,
                recipient.getLatitude(),
                recipient.getLongitude()
        );

        return new AutoMatchCandidateResponse(
                activity.getId(),
                AddressUtils.extractRegion(recipient.getAddress()),
                AddressUtils.calculateAgeGroup(recipient.getBirthYear()),
                recipient.getGender().name(),
                activity.getScheduledAt(),
                activity.getRequiredPeople(),
                approvedCount,
                applicantCount,
                distanceKm,
                reason,
                model
        );
    }
}