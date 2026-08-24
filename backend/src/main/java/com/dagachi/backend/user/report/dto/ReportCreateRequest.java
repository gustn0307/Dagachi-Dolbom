package com.dagachi.backend.user.report.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReportCreateRequest(

        @NotBlank(message = "제보 내용은 필수입니다.")
        String content,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        @Digits(integer = 3, fraction = 7, message = "위도 형식이 올바르지 않습니다.")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        @Digits(integer = 3, fraction = 7, message = "경도 형식이 올바르지 않습니다.")
        BigDecimal longitude,

        @Size(max = 30, message = "연락처는 30자 이하여야 합니다.")
        String guestPhone
) {
}