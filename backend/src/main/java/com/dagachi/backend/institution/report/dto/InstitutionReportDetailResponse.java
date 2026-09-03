package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InstitutionReportDetailResponse(
        Long reportId,
        String content,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReportDetailImageResponse> images,
        ReportDetailRecipientResponse recipient,
        ReportDetailAiSummaryResponse aiSummary
) {

    public static InstitutionReportDetailResponse from(
            Report report,
            List<ReportDetailImageResponse> images,
            ReportDetailAiSummaryResponse aiSummary
    ) {
        return new InstitutionReportDetailResponse(
                report.getId(),
                report.getContent(),
                report.getAddress(),
                report.getLatitude(),
                report.getLongitude(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                images,
                ReportDetailRecipientResponse.from(
                        report.getCareRecipient()
                ),
                aiSummary
        );
    }
}