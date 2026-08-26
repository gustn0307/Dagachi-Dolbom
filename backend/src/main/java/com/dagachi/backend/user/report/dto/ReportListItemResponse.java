package com.dagachi.backend.user.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.time.LocalDateTime;

public record ReportListItemResponse(
        Long reportId,
        String content,
        String address,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ReportListItemResponse from(Report report) {
        return new ReportListItemResponse(
                report.getId(),
                report.getContent(),
                report.getAddress(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}