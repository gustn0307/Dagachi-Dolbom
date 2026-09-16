package com.dagachi.backend.institution.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InstitutionDashboardResponse(
        String managerName,
        DashboardSummary summary,
        List<CompletedTrendPoint> completedTrend,
        List<UpcomingActivity> upcomingActivities
) {
    public record DashboardSummary(
            long volunteerCount,
            long careRecipientCount,
            long totalActivityCount,
            long pendingApplicationCount,
            long inProgressActivityCount,
            long completedActivityCount,
            long unassignedReportCount,
            long pendingRecordReviewCount
    ) {
    }

    public record CompletedTrendPoint(
            String key,
            String label,
            long count
    ) {
    }

    public record UpcomingActivity(
            Long activityId,
            String recipientName,
            LocalDateTime scheduledAt,
            int requiredPeople,
            long approvedPeople,
            String status
    ) {
    }
}
