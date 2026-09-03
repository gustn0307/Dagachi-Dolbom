package com.dagachi.backend.user.application.dto;

import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;

import java.time.LocalDateTime;

/**
 * APP-01 신청 응답과 APP-03 내 신청 목록 조회 응답을 함께 담당하는 DTO.
 * activity/recipient 정보까지 포함해 신청 자체의 정보 + 활동 요약을 한 번에 제공한다.
 */
public record ApplicationResponse(
        Long applicationId,
        ApplicationType applicationType,
        ApplicationStatus status,
        String rejectedReason,
        LocalDateTime createdAt,
        Long activityId,
        String region,
        String ageGroup,
        String gender,
        LocalDateTime scheduledAt,
        ActivityStatus activityStatus
) {
    public static ApplicationResponse from(ActivityApplication application) {
        CareActivity activity = application.getActivity();
        var recipient = activity.getRecipient();

        return new ApplicationResponse(
                application.getId(),
                application.getApplicationType(),
                application.getStatus(),
                application.getRejectedReason(),
                application.getCreatedAt(),
                activity.getId(),
                AddressUtils.extractRegion(recipient.getAddress()),
                AddressUtils.calculateAgeGroup(recipient.getBirthYear()),
                recipient.getGender().name(),
                activity.getScheduledAt(),
                activity.getStatus()
        );
    }
}