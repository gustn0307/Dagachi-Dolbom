package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.common.util.GeoUtils;
import com.dagachi.backend.domain.entity.CareActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACT-01 목록용 공개 활동 DTO.
 * 정확한 주소·좌표·연락처는 절대 포함하지 않는다.
 */
public record ActivityResponse(
        Long activityId,
        String region,
        String ageGroup,
        String gender,
        LocalDateTime scheduledAt,
        Integer requiredPeople,
        Long approvedCount,
        String status,
        String genderCondition,
        BigDecimal distanceKm
) {

    public static ActivityResponse of(
            CareActivity activity,
            long approvedCount,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        var recipient = activity.getRecipient();

        BigDecimal distanceKm = GeoUtils.calculateDistanceKm(
                userLatitude,
                userLongitude,
                recipient.getLatitude(),
                recipient.getLongitude()
        );

        return new ActivityResponse(
                activity.getId(),
                AddressUtils.extractRegion(recipient.getAddress()),
                AddressUtils.calculateAgeGroup(recipient.getBirthYear()),
                recipient.getGender().name(),
                activity.getScheduledAt(),
                activity.getRequiredPeople(),
                approvedCount,
                activity.getStatus().name(),
                activity.getGenderCondition().name(),
                distanceKm
        );
    }
}