package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.user.activity.dto.ActivityDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityExecutionDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityResponse;
import com.dagachi.backend.user.activity.dto.ActivitySearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ActivityService {

    /** dateFrom 필터가 없을 때 사용하는 사실상 무제한 하한. */
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    /** dateTo 필터가 없을 때 사용하는 사실상 무제한 상한. */
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final CareActivityRepository careActivityRepository;
    private final ActivityApplicationRepository activityApplicationRepository;

    public ActivityService(
            CareActivityRepository careActivityRepository,
            ActivityApplicationRepository activityApplicationRepository
    ) {
        this.careActivityRepository = careActivityRepository;
        this.activityApplicationRepository = activityApplicationRepository;
    }

    public PageResponse<ActivityResponse> getActivities(
            ActivitySearchCondition condition,
            Pageable pageable
    ) {
        if (condition.hasCoordinates()) {
            return PageResponse.from(getActivitiesSortedByDistance(condition, pageable));
        }

        String region = normalizeRegion(condition.region());
        LocalDateTime dateFrom = resolveDateFrom(condition.dateFrom());
        LocalDateTime dateTo = resolveDateTo(condition.dateTo());

        Page<CareActivity> activityPage = careActivityRepository.findRecruitingActivitiesPaged(
                region, dateFrom, dateTo, pageable
        );

        Map<Long, Long> approvedCountMap = getApprovedCountMap(activityPage.getContent());

        Page<ActivityResponse> responsePage = activityPage.map(activity ->
                ActivityResponse.of(
                        activity,
                        approvedCountMap.getOrDefault(activity.getId(), 0L),
                        null,
                        null
                )
        );

        return PageResponse.from(responsePage);
    }

    private Page<ActivityResponse> getActivitiesSortedByDistance(
            ActivitySearchCondition condition,
            Pageable pageable
    ) {
        String region = normalizeRegion(condition.region());
        LocalDateTime dateFrom = resolveDateFrom(condition.dateFrom());
        LocalDateTime dateTo = resolveDateTo(condition.dateTo());

        List<CareActivity> activities = careActivityRepository.findRecruitingActivitiesForDistanceSort(
                region, dateFrom, dateTo
        );

        Map<Long, Long> approvedCountMap = getApprovedCountMap(activities);

        List<ActivityResponse> sorted = activities.stream()
                .map(activity -> ActivityResponse.of(
                        activity,
                        approvedCountMap.getOrDefault(activity.getId(), 0L),
                        condition.latitude(),
                        condition.longitude()
                ))
                .sorted(
                        Comparator.comparing(
                                        ActivityResponse::distanceKm,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparing(ActivityResponse::scheduledAt)
                )
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), sorted.size());

        List<ActivityResponse> pageContent = start >= sorted.size()
                ? List.of()
                : sorted.subList(start, end);

        return new PageImpl<>(pageContent, pageable, sorted.size());
    }

    private String normalizeRegion(String region) {
        return region == null ? "" : region;
    }

    private LocalDateTime resolveDateFrom(LocalDate dateFrom) {
        return dateFrom != null ? dateFrom.atStartOfDay() : MIN_DATE;
    }

    private LocalDateTime resolveDateTo(LocalDate dateTo) {
        return dateTo != null ? dateTo.plusDays(1).atStartOfDay() : MAX_DATE;
    }

    // ---- ACT-02, ACT-03은 기존 그대로 ----

    public ActivityDetailResponse getActivityDetail(Long activityId, Long userId) {
        CareActivity activity = findActivity(activityId);

        long approvedCount = activityApplicationRepository
                .countApprovedMap(List.of(activityId))
                .getOrDefault(activityId, 0L);

        ActivityResponse base = ActivityResponse.of(activity, approvedCount, null, null);

        String myApplicationStatus = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .map(application -> application.getStatus().name())
                .orElse(null);

        return ActivityDetailResponse.of(base, myApplicationStatus);
    }

    public ActivityExecutionDetailResponse getExecutionDetail(Long activityId, Long userId) {
        CareActivity activity = findActivity(activityId);

        ActivityApplication application = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));

        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        long approvedCount = activityApplicationRepository
                .countApprovedMap(List.of(activityId))
                .getOrDefault(activityId, 0L);

        return ActivityExecutionDetailResponse.of(activity, approvedCount);
    }

    private CareActivity findActivity(Long activityId) {
        return careActivityRepository.findDetailById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Map<Long, Long> getApprovedCountMap(List<CareActivity> activities) {
        if (activities.isEmpty()) {
            return Map.of();
        }

        List<Long> activityIds = activities.stream()
                .map(CareActivity::getId)
                .collect(Collectors.toList());

        return activityApplicationRepository.countApprovedMap(activityIds);
    }
}