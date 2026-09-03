package com.dagachi.backend.institution.activity.dto;

import com.dagachi.backend.domain.enums.GenderCondition;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 기관 활동 등록 요청.
 */
public record InstitutionActivityCreateRequest(

        @NotNull(message = "돌봄 대상자는 필수입니다.")
        Long recipientId,

        @NotNull(message = "활동 예정 일시는 필수입니다.")
        @Future(message = "활동 예정 일시는 현재보다 이후여야 합니다.")
        LocalDateTime scheduledAt,

        @NotNull(message = "모집 인원은 필수입니다.")
        @Min(
                value = 2,
                message = "모집 인원은 최소 2명이어야 합니다."
        )
        Integer requiredPeople,

        @NotNull(message = "성별 조건은 필수입니다.")
        GenderCondition genderCondition
) {
}