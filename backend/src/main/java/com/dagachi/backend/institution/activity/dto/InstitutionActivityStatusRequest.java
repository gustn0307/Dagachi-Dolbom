package com.dagachi.backend.institution.activity.dto;

import com.dagachi.backend.domain.enums.ActivityStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 기관 활동 상태 변경 요청.
 */
public record InstitutionActivityStatusRequest(

        @NotNull(message = "변경할 활동 상태는 필수입니다.")
        ActivityStatus status
) {
}