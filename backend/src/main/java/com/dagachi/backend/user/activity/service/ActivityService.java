package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
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
    private final ActivityRecordRepository activityRecordRepository;

    public ActivityService(
            CareActivityRepository careActivityRepository,
            ActivityApplicationRepository activityApplicationRepository,
            ActivityRecordRepository activityRecordRepository
    ) {
        this.careActivityRepository = careActivityRepository;
        this.activityApplicationRepository = activityApplicationRepository;
        this.activityRecordRepository = activityRecordRepository;
    }

    public PageResponse<ActivityResponse> getActivities(
            ActivitySearchCondition condition,
            Pageable pageable,
            Long userId
    ) {
        boolean hasAgeGroups = condition.hasAgeGroups();
        List<Integer> ageBuckets = resolveAgeBuckets(condition.ageGroups());
        int currentYear = LocalDate.now().getYear();
        boolean hasGender = condition.hasGender();
        UserGender gender = resolveGender(condition.gender());

        // 좌표 기반 거리순이 안부 오래된순보다 우선한다.
        if (condition.hasCoordinates()) {
            return PageResponse.from(
                    getActivitiesSortedByDistance(
                            condition, pageable, hasAgeGroups, ageBuckets, currentYear, hasGender, gender, userId
                    )
            );
        }

        if (condition.isStaleSort()) {
            return PageResponse.from(
                    getActivitiesSortedByStaleness(
                            condition, pageable, hasAgeGroups, ageBuckets, currentYear, hasGender, gender, userId
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
        Map<Long, Long> applicantCountMap = getApplicantCountMap(activityPage.getContent());
        Map<Long, String> myStatusMap = getMyApplicationStatusMap(userId, activityPage.getContent());

        Page<ActivityResponse> responsePage = activityPage.map(activity ->
                ActivityResponse.of(
                        activity,
                        approvedCountMap.getOrDefault(activity.getId(), 0L),
                        applicantCountMap.getOrDefault(activity.getId(), 0L),
                        null,
                        null,
                        myStatusMap.get(activity.getId())
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
            UserGender gender,
            Long userId
    ) {
        List<CareActivity> activities = fetchUnpagedActivities(
                condition, hasAgeGroups, ageBuckets, currentYear, hasGender, gender
        );

        Map<Long, Long> approvedCountMap = getApprovedCountMap(activities);
        Map<Long, Long> applicantCountMap = getApplicantCountMap(activities);
        Map<Long, String> myStatusMap = getMyApplicationStatusMap(userId, activities);

        List<ActivityResponse> sorted = activities.stream()
                .map(activity -> ActivityResponse.of(
                        activity,
                        approvedCountMap.getOrDefault(activity.getId(), 0L),
                        applicantCountMap.getOrDefault(activity.getId(), 0L),
                        condition.latitude(),
                        condition.longitude(),
                        myStatusMap.get(activity.getId())
                ))
                .sorted(
                        Comparator.comparing(
                                        ActivityResponse::distanceKm,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparing(ActivityResponse::scheduledAt)
                )
                .collect(Collectors.toList());

        return toPage(sorted, pageable);
    }

    /**
     * 대상자의 최근 안부 확인일(lastCheckedAt)이 오래된 순으로 정렬한다.
     * 한 번도 안부 확인이 안 된 대상자(null)는 가장 오래된 것으로 간주해 최우선 노출한다.
     * (getAutoMatchCandidate()의 staleRank 로직과 동일한 규칙)
     */
    private Page<ActivityResponse> getActivitiesSortedByStaleness(
            ActivitySearchCondition condition,
            Pageable pageable,
            boolean hasAgeGroups,
            List<Integer> ageBuckets,
            int currentYear,
            boolean hasGender,
            UserGender gender,
            Long userId
    ) {
        List<CareActivity> activities = fetchUnpagedActivities(
                condition, hasAgeGroups, ageBuckets, currentYear, hasGender, gender
        );

        Map<Long, Long> approvedCountMap = getApprovedCountMap(activities);
        Map<Long, Long> applicantCountMap = getApplicantCountMap(activities);
        Map<Long, String> myStatusMap = getMyApplicationStatusMap(userId, activities);

        List<ActivityResponse> sorted = activities.stream()
                .sorted(Comparator.comparing((CareActivity activity) -> {
                    LocalDateTime lastCheckedAt = activity.getRecipient().getLastCheckedAt();
                    return lastCheckedAt != null ? lastCheckedAt : LocalDateTime.MIN;
                }).thenComparing(CareActivity::getScheduledAt))
                .map(activity -> ActivityResponse.of(
                        activity,
                        approvedCountMap.getOrDefault(activity.getId(), 0L),
                        applicantCountMap.getOrDefault(activity.getId(), 0L),
                        null,
                        null,
                        myStatusMap.get(activity.getId())
                ))
                .collect(Collectors.toList());

        return toPage(sorted, pageable);
    }

    /**
     * 거리순/안부 오래된순처럼 자바 메모리 정렬이 필요한 경우 공통으로 쓰는,
     * 페이징 없이 필터 조건에 맞는 전체 활동 목록 조회.
     */
    private List<CareActivity> fetchUnpagedActivities(
            ActivitySearchCondition condition,
            boolean hasAgeGroups,
            List<Integer> ageBuckets,
            int currentYear,
            boolean hasGender,
            UserGender gender
    ) {
        String region = normalizeRegion(condition.region());
        LocalDateTime dateFrom = resolveDateFrom(condition.dateFrom());
        LocalDateTime dateTo = resolveDateTo(condition.dateTo());

        return careActivityRepository.findRecruitingActivitiesForDistanceSort(
                region, dateFrom, dateTo, hasAgeGroups, ageBuckets, currentYear, hasGender, gender
        );
    }

    /**
     * 자바에서 이미 정렬을 마친 전체 리스트를 Pageable 기준으로 잘라 Page로 감싼다.
     */
    private Page<ActivityResponse> toPage(List<ActivityResponse> sorted, Pageable pageable) {
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

    /**
     * 목록에 나온 활동들에 대한 로그인 유저 본인의 신청 상태를 조회한다.
     * 취소(CANCELED)된 신청은 "신청 안 한 것"과 동일하게 취급해 제외한다.
     * 비로그인(userId == null)이거나 활동이 없으면 빈 Map을 반환한다.
     */
    private Map<Long, String> getMyApplicationStatusMap(Long userId, List<CareActivity> activities) {
        if (userId == null || activities.isEmpty()) {
            return Map.of();
        }

        List<Long> activityIds = activities.stream()
                .map(CareActivity::getId)
                .collect(Collectors.toList());

        return activityApplicationRepository
                .findActiveApplicationsByUserAndActivityIds(userId, activityIds)
                .stream()
                .collect(Collectors.toMap(
                        application -> application.getActivity().getId(),
                        application -> application.getStatus().name()
                ));
    }

    /**
     * 활동별 "신청자수"(대기중 + 승인됨)를 집계한다. 취소/거절은 제외한다.
     */
    private Map<Long, Long> getApplicantCountMap(List<CareActivity> activities) {
        if (activities.isEmpty()) {
            return Map.of();
        }

        List<Long> activityIds = activities.stream()
                .map(CareActivity::getId)
                .collect(Collectors.toList());

        return activityApplicationRepository
                .findActiveApplicationsByActivityIds(activityIds)
                .stream()
                .collect(Collectors.groupingBy(
                        application -> application.getActivity().getId(),
                        Collectors.counting()
                ));
    }

    // ---- ACT-02, ACT-03은 기존 그대로 ----

    public ActivityDetailResponse getActivityDetail(Long activityId, Long userId) {
        CareActivity activity = findActivity(activityId);

        long approvedCount = activityApplicationRepository
                .countApprovedMap(List.of(activityId))
                .getOrDefault(activityId, 0L);

        long applicantCount = activityApplicationRepository
                .findActiveApplicationsByActivityIds(List.of(activityId))
                .size();

        String myApplicationStatus = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .map(application -> application.getStatus().name())
                .orElse(null);

        ActivityResponse base = ActivityResponse.of(
                activity, approvedCount, applicantCount, null, null, myApplicationStatus
        );

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

        // 이미 다른 참여자가 활동을 시작해 IN_PROGRESS 상태라면
        // 기존 ActivityRecord id를 함께 내려줘서 체크리스트로 바로 진입할 수 있게 한다.
        // 아직 시작 전(READY)이면 Record가 없으므로 null.
        Long activityRecordId = activityRecordRepository
                .findByActivity_Id(activityId)
                .map(ActivityRecord::getId)
                .orElse(null);

        return ActivityExecutionDetailResponse.of(activity, approvedCount, activityRecordId);
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