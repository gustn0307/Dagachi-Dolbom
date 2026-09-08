package com.dagachi.backend.institution.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstitutionActivityRecordRevisionRequest(

        @NotBlank(
                message = "보완 요청 사유는 필수입니다."
        )
        @Size(
                max = 500,
                message = "보완 요청 사유는 500자 이하여야 합니다."
        )
        String reviewNote
) {
}