package com.dagachi.backend.institution.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기관 담당자의 봉사 신청 반려 요청.
 */
public record InstitutionActivityApplicationRejectRequest(

        /*
         * 반려 사유는 반드시 입력해야 하며
         * 최대 500자까지 허용한다.
         */
        @NotBlank(
                message = "반려 사유는 필수입니다."
        )
        @Size(
                max = 500,
                message = "반려 사유는 500자 이하여야 합니다."
        )
        String reason
) {
}