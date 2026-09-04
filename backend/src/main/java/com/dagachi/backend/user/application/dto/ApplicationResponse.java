package com.dagachi.backend.user.application.dto;

import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;

import java.time.LocalDateTime;

/**
 * APP-01/03/04/05 응답을 함께 담당하는 DTO.
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
        ActivityStatus activityStatus,
        boolean cancelable,
        boolean reapplicable
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
                activity.getStatus(),
                isCancelable(application, activity),
                isReapplicable(application, activity)
        );
    }

    // APP-05 스펙: PENDING 또는 (활동이 시작 전인) APPROVED만 취소 가능
    private static boolean isCancelable(ActivityApplication application, CareActivity activity) {
        if (application.getStatus() == ApplicationStatus.PENDING) {
            return true;
        }
        if (application.getStatus() == ApplicationStatus.APPROVED) {
            return activity.getStatus() == ActivityStatus.RECRUITING
                    || activity.getStatus() == ActivityStatus.READY;
        }
        return false;
    }

    // 취소된 신청이고, 활동이 여전히 모집 중(RECRUITING/READY)일 때만 재신청 가능
    private static boolean isReapplicable(ActivityApplication application, CareActivity activity) {
        if (application.getStatus() != ApplicationStatus.CANCELED) {
            return false;
        }
        return activity.getStatus() == ActivityStatus.RECRUITING
                || activity.getStatus() == ActivityStatus.READY;
    }
}