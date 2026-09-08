package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.time.LocalDateTime;

/**
 * REPORT-05 제보 상태 변경 결과입니다.
 */
public record ReportStatusUpdateResponse(
        Long reportId,
        ReportStatus status,
        LocalDateTime updatedAt
) {

    public static ReportStatusUpdateResponse from(
            Report report
    ) {
        return new ReportStatusUpdateResponse(
                report.getId(),
                report.getStatus(),
                report.getUpdatedAt()
        );
    }
}