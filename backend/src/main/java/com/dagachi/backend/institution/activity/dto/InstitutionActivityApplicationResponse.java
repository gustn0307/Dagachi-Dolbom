package com.dagachi.backend.institution.activity.dto;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.domain.enums.UserGender;

import java.time.LocalDateTime;

/**
 * 기관 활동 신청자 목록 응답.
 */
public record InstitutionActivityApplicationResponse(
        Long applicationId,
        Long userId,
        String name,
        String nickname,
        String phone,
        UserGender gender,
        ApplicationType applicationType,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        Long approvedById,
        String approvedByName,
        LocalDateTime approvedAt,
        String rejectedReason
) {

    public static InstitutionActivityApplicationResponse from(
            ActivityApplication application
    ) {
        User volunteer =
                application.getUser();

        User approvedBy =
                application.getApprovedBy();

        return new InstitutionActivityApplicationResponse(
                application.getId(),
                volunteer.getId(),
                volunteer.getName(),
                volunteer.getNickname(),
                volunteer.getPhone(),
                volunteer.getGender(),
                application.getApplicationType(),
                application.getStatus(),
                application.getCreatedAt(),
                approvedBy == null
                        ? null
                        : approvedBy.getId(),
                approvedBy == null
                        ? null
                        : approvedBy.getName(),
                application.getApprovedAt(),
                application.getRejectedReason()
        );
    }
}