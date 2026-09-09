package com.dagachi.backend.user.application.dto;

import jakarta.validation.constraints.NotNull;

// APP-02 2단계 확정 요청. GET candidate에서 받은 activityId를 그대로 전달한다.
public record AutoMatchApplyRequest(
        @NotNull(message = "activityId는 필수입니다.")
        Long activityId
) {
}