package com.dagachi.backend.institution.dashboard.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.dashboard.dto.DashboardPeriod;
import com.dagachi.backend.institution.dashboard.dto.InstitutionDashboardResponse;
import com.dagachi.backend.institution.dashboard.repository.InstitutionDashboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InstitutionDashboardService {

    private final UserRepository userRepository;
    private final InstitutionDashboardRepository dashboardRepository;

    public InstitutionDashboardService(
            UserRepository userRepository,
            InstitutionDashboardRepository dashboardRepository
    ) {
        this.userRepository = userRepository;
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    public InstitutionDashboardResponse getDashboard(Long userId, DashboardPeriod period) {
        User manager = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        Institution institution = manager.getInstitution();
        if (institution == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Long institutionId = institution.getId();
        InstitutionDashboardResponse.DashboardSummary summary =
                new InstitutionDashboardResponse.DashboardSummary(
                        dashboardRepository.countParticipants(institutionId),
                        dashboardRepository.countRecipients(institutionId),
                        dashboardRepository.countActivities(institutionId),
                        dashboardRepository.countPendingApplications(institutionId),
                        dashboardRepository.countActivitiesByStatus(institutionId, "IN_PROGRESS"),
                        dashboardRepository.countActivitiesByStatus(institutionId, "COMPLETED"),
                        dashboardRepository.countUnassignedReports(),
                        dashboardRepository.countPendingRecordReviews(institutionId)
                );

        List<InstitutionDashboardResponse.UpcomingActivity> upcomingActivities =
                dashboardRepository.findUpcomingActivities(institutionId).stream()
                        .map(row -> new InstitutionDashboardResponse.UpcomingActivity(
                                row.activityId(), row.recipientName(), row.scheduledAt(),
                                row.requiredPeople(), row.approvedPeople(), row.status()))
                        .toList();

        return new InstitutionDashboardResponse(
                manager.getName(),
                summary,
                buildTrend(institutionId, period),
                upcomingActivities
        );
    }

    private List<InstitutionDashboardResponse.CompletedTrendPoint> buildTrend(
            Long institutionId,
            DashboardPeriod period
    ) {
        LocalDate today = LocalDate.now();
        Map<String, InstitutionDashboardResponse.CompletedTrendPoint> points = new LinkedHashMap<>();
        String datePart;
        LocalDateTime from;

        if (period == DashboardPeriod.DAILY) {
            LocalDate start = today.minusDays(13);
            datePart = "day";
            from = start.atStartOfDay();
            for (int i = 0; i < 14; i++) {
                LocalDate date = start.plusDays(i);
                String key = date.toString();
                points.put(key, new InstitutionDashboardResponse.CompletedTrendPoint(
                        key, date.format(DateTimeFormatter.ofPattern("M/d")), 0));
            }
        } else if (period == DashboardPeriod.YEARLY) {
            int startYear = today.getYear() - 4;
            datePart = "year";
            from = LocalDate.of(startYear, 1, 1).atStartOfDay();
            for (int year = startYear; year <= today.getYear(); year++) {
                String key = Integer.toString(year);
                points.put(key, new InstitutionDashboardResponse.CompletedTrendPoint(
                        key, year + "년", 0));
            }
        } else {
            YearMonth start = YearMonth.from(today).minusMonths(11);
            datePart = "month";
            from = start.atDay(1).atStartOfDay();
            for (int i = 0; i < 12; i++) {
                YearMonth month = start.plusMonths(i);
                String key = month.toString();
                points.put(key, new InstitutionDashboardResponse.CompletedTrendPoint(
                        key, month.format(DateTimeFormatter.ofPattern("M월")), 0));
            }
        }

        dashboardRepository.findCompletedTrend(institutionId, datePart, from)
                .forEach(row -> {
                    String key = switch (period) {
                        case DAILY -> row.bucket().toLocalDate().toString();
                        case MONTHLY -> YearMonth.from(row.bucket()).toString();
                        case YEARLY -> Integer.toString(row.bucket().getYear());
                    };
                    InstitutionDashboardResponse.CompletedTrendPoint old = points.get(key);
                    if (old != null) {
                        points.put(key, new InstitutionDashboardResponse.CompletedTrendPoint(
                                key, old.label(), row.count()));
                    }
                });

        return List.copyOf(points.values());
    }
}
