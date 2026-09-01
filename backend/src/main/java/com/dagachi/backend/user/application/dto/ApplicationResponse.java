package com.dagachi.backend.user.application.dto;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;

import java.time.LocalDateTime;

public record ApplicationResponse(
        Long applicationId,
        Long activityId,
        ApplicationType applicationType,
        ApplicationStatus status,
        LocalDateTime createdAt
) {
    public static ApplicationResponse from(ActivityApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getActivity().getId(),
                application.getApplicationType(),
                application.getStatus(),
                application.getCreatedAt()
        );
    }
}