package com.dagachi.backend.user.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.time.LocalDateTime;

public record ReportCreateResponse(
        Long reportId,
        ReportStatus status,
        LocalDateTime createdAt
) {

    public static ReportCreateResponse from(Report report) {
        return new ReportCreateResponse(
                report.getId(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}