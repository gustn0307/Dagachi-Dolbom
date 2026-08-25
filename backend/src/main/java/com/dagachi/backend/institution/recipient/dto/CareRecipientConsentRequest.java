package com.dagachi.backend.institution.recipient.dto;

import com.dagachi.backend.domain.enums.ConsentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * CARE-05 돌봄 대상자 동의 상태 변경 요청.
 */
public record CareRecipientConsentRequest(

        @NotNull(message = "동의 상태는 필수입니다.")
        ConsentStatus consentStatus
) {
}