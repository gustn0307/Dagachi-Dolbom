package com.dagachi.backend.user.report.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ReportCreateRequest(

        @NotBlank(message = "제보 내용은 필수입니다.")
        String content,

        String address,

        BigDecimal latitude,

        BigDecimal longitude,

        String guestPhone
) {
}