package com.dagachi.backend.institution.activity.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * ACT-06 기관 활동 정보 수정 요청.
 */
public record InstitutionActivityUpdateRequest(

        @NotNull(message = "활동 예정 일시는 필수입니다.")
        @Future(message = "활동 예정 일시는 현재보다 이후여야 합니다.")
        LocalDateTime scheduledAt,

        @NotNull(message = "필요 인원은 필수입니다.")
        @Min(
                value = 1,
                message = "필요 인원은 1명 이상이어야 합니다."
        )
        Integer requiredPeople
) {
}