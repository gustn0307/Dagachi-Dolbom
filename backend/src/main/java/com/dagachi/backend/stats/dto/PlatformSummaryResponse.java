package com.dagachi.backend.stats.dto;

public record PlatformSummaryResponse(
        long totalCitizens,             // role=USER, status=ACTIVE 회원 수
        long totalCompletedActivities,  // reviewStatus=APPROVED ActivityRecord 건수
        long totalCareRecipients        // status=ACTIVE CareRecipient 수
) {
}