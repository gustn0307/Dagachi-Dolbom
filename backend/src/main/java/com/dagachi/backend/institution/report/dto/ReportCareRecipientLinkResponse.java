package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

/**
 * REPORT-06 기존 돌봄 대상자 연결 결과입니다.
 */
public record ReportCareRecipientLinkResponse(
        Long reportId,
        Long careRecipientId,
        ReportStatus status
) {

    public static ReportCareRecipientLinkResponse from(
            Report report
    ) {
        return new ReportCareRecipientLinkResponse(
                report.getId(),
                report.getCareRecipient().getId(),
                report.getStatus()
        );
    }
}