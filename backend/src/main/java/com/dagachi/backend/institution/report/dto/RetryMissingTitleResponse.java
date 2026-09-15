package com.dagachi.backend.institution.report.dto;

import java.util.List;

public record RetryMissingTitleResponse(
        int targetCount,
        int succeededCount,
        int failedCount,
        List<Long> failedReportIds,
        boolean hasMore
) {
}