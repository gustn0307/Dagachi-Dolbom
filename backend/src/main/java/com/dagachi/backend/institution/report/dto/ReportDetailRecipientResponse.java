package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.UserGender;

public record ReportDetailRecipientResponse(
        Long recipientId,
        String name,
        UserGender gender,
        Integer birthYear,
        CareRecipientStatus status,
        ConsentStatus consentStatus
) {

    public static ReportDetailRecipientResponse from(
            CareRecipient recipient
    ) {
        if (recipient == null) {
            return null;
        }

        return new ReportDetailRecipientResponse(
                recipient.getId(),
                recipient.getName(),
                recipient.getGender(),
                recipient.getBirthYear(),
                recipient.getStatus(),
                recipient.getConsentStatus()
        );
    }
}