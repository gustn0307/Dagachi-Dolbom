package com.dagachi.backend.institution.recipient.dto;

import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.UserGender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CareRecipientCreateRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "성별은 필수입니다.")
        UserGender gender,

        @Min(value = 1900, message = "출생연도가 올바르지 않습니다.")
        @Max(value = 2100, message = "출생연도가 올바르지 않습니다.")
        Integer birthYear,

        @Size(max = 30, message = "전화번호는 30자 이하여야 합니다.")
        String phone,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
        String detailAddress,

        BigDecimal latitude,

        BigDecimal longitude,

        @NotNull(message = "동의 상태는 필수입니다.")
        ConsentStatus consentStatus
) {
}