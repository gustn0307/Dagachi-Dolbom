package com.dagachi.backend.institution.profile.dto;

import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.enums.InstitutionStatus;

public record InstitutionProfileResponse(
        Long id,
        String name,
        String type,
        String address,
        String phone,
        InstitutionStatus status
) {
    public static InstitutionProfileResponse from(Institution institution) {
        return new InstitutionProfileResponse(
                institution.getId(),
                institution.getName(),
                institution.getType().name(),
                institution.getAddress(),
                institution.getPhone(),
                institution.getStatus()
        );
    }
}