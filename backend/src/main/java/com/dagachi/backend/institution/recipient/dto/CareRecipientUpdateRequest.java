package com.dagachi.backend.institution.recipient.dto;

import com.dagachi.backend.domain.enums.UserGender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * CARE-04 돌봄 대상자 기본정보 수정 요청.
 *
 * null인 필드는 기존 값을 유지한다.
 * 관리 상태와 동의 상태는 별도 API에서 변경한다.
 */
public record CareRecipientUpdateRequest(

        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "이름은 공백일 수 없습니다."
        )
        String name,

        UserGender gender,

        @Min(value = 1900, message = "출생연도가 올바르지 않습니다.")
        @Max(value = 2100, message = "출생연도가 올바르지 않습니다.")
        Integer birthYear,

        @Size(max = 30, message = "전화번호는 30자 이하여야 합니다.")
        String phone,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "주소는 공백일 수 없습니다."
        )
        String address,

        @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
        String detailAddress,

        @DecimalMin(
                value = "-90.0",
                message = "위도는 -90 이상이어야 합니다."
        )
        @DecimalMax(
                value = "90.0",
                message = "위도는 90 이하여야 합니다."
        )
        BigDecimal latitude,

        @DecimalMin(
                value = "-180.0",
                message = "경도는 -180 이상이어야 합니다."
        )
        @DecimalMax(
                value = "180.0",
                message = "경도는 180 이하여야 합니다."
        )
        BigDecimal longitude
) {
}