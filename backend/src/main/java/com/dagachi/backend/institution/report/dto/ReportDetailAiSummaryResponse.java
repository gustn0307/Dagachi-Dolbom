package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.AIAnalysis;

import java.time.LocalDateTime;

public record ReportDetailAiSummaryResponse(
        Long analysisId,
        String summary,
        String model,
        LocalDateTime createdAt
) {

    public static ReportDetailAiSummaryResponse from(
            AIAnalysis analysis,
            String summary
    ) {
        if (analysis == null) {
            return null;
        }

        return new ReportDetailAiSummaryResponse(
                analysis.getId(),
                summary,
                analysis.getModelName(),
                analysis.getCreatedAt()
        );
    }
}