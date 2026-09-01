package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.UserGender;
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
    /** ageGroups 필터가 없을 때 사용하는, 실제 버킷값(50/60/70/80/90)과 겹치지 않는 sentinel. */
    private static final List<Integer> NO_AGE_GROUP_FILTER = List.of(-1);

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
        boolean hasAgeGroups = condition.hasAgeGroups();
        List<Integer> ageBuckets = resolveAgeBuckets(condition.ageGroups());
        int currentYear = LocalDate.now().getYear();
        boolean hasGender = condition.hasGender();
        UserGender gender = resolveGender(condition.gender());

        if (condition.hasCoordinates()) {
            return PageResponse.from(
                    getActivitiesSortedByDistance(
                            condition, pageable, hasAgeGroups, ageBuckets, currentYear, hasGender, gender
                    )
            );
        }

        String region = normalizeRegion(condition.region());
        LocalDateTime dateFrom = resolveDateFrom(condition.dateFrom());
        LocalDateTime dateTo = resolveDateTo(condition.dateTo());

        Page<CareActivity> activityPage = careActivityRepository.findRecruitingActivitiesPaged(
                region, dateFrom, dateTo, hasAgeGroups, ageBuckets, currentYear, hasGender, gender, pageable
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
            Pageable pageable,
            boolean hasAgeGroups,
            List<Integer> ageBuckets,
            int currentYear,
            boolean hasGender,
            UserGender gender
    ) {
        String region = normalizeRegion(condition.region());
        LocalDateTime dateFrom = resolveDateFrom(condition.dateFrom());
        LocalDateTime dateTo = resolveDateTo(condition.dateTo());

        List<CareActivity> activities = careActivityRepository.findRecruitingActivitiesForDistanceSort(
                region, dateFrom, dateTo, hasAgeGroups, ageBuckets, currentYear, hasGender, gender
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

    /**
     * "60대" 등 라벨을 버킷 대표값(60) 리스트로 변환한다.
     * 허용되지 않는 라벨이 하나라도 있으면 400.
     * 선택된 연령대가 없으면 절대 매칭되지 않는 sentinel을 반환해
     * Repository에서 빈 컬렉션을 IN 파라미터로 바인딩하는 문제를 피한다.
     */
    private List<Integer> resolveAgeBuckets(List<String> ageGroups) {
        if (ageGroups == null || ageGroups.isEmpty()) {
            return NO_AGE_GROUP_FILTER;
        }

        List<Integer> buckets = ageGroups.stream()
                .map(AddressUtils::parseAgeGroupBucket)
                .collect(Collectors.toList());

        if (buckets.contains(null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return buckets;
    }

    /**
     * 성별 필터 문자열("MALE"/"FEMALE")을 UserGender로 변환한다.
     * 값이 없으면 null(hasGender=false일 때만 호출부에서 무시됨).
     * 허용되지 않는 값이면 400.
     */
    private UserGender resolveGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return UserGender.valueOf(gender);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
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