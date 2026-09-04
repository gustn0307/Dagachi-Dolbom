package com.dagachi.backend.institution.report.dto;

import jakarta.validation.constraints.NotNull;

/**
 * REPORT-06 기존 돌봄 대상자 연결 요청입니다.
 */
public record ReportCareRecipientLinkRequest(

        @NotNull
        Long careRecipientId

) {
}