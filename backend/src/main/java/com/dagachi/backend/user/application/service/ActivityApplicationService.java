package com.dagachi.backend.user.application.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.common.util.GeoUtils;
import com.dagachi.backend.user.activity.dto.ActivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.Comparator;

/**
 * 일반 USER의 활동 신청(APP-01) / 내 신청 목록(APP-03) / 내 활동 목록(APP-04)
 * / 신청 취소(APP-05) 비즈니스 로직을 담당한다.
 */
@Service
public class ActivityApplicationService {

    private final ActivityApplicationRepository activityApplicationRepository;
    private final CareActivityRepository careActivityRepository;
    private final UserRepository userRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private static final List<Long> NO_EXCLUDE_FILTER = List.of(-1L);

    public ActivityApplicationService(
            ActivityApplicationRepository activityApplicationRepository,
            CareActivityRepository careActivityRepository,
            UserRepository userRepository,
            ActivityRecordRepository activityRecordRepository
    ) {
        this.activityApplicationRepository = activityApplicationRepository;
        this.careActivityRepository = careActivityRepository;
        this.userRepository = userRepository;
        this.activityRecordRepository = activityRecordRepository;
    }

    /**
     * APP-01 직접 신청.
     */
    @Transactional
    public ApplicationResponse applyDirect(Long activityId, Long userId) {

        CareActivity activity = findActivity(activityId);

        if (activity.getStatus() != ActivityStatus.RECRUITING
                && activity.getStatus() != ActivityStatus.READY) {
            throw new CustomException(ErrorCode.ACTIVITY_NOT_RECRUITING);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ActivityApplication application = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .orElse(null);

        if (application == null) {
            application = ActivityApplication.createDirect(activity, user);
        } else if (application.getStatus() == ApplicationStatus.CANCELED) {
            application.reactivate();
        } else {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }

        ActivityApplication saved = activityApplicationRepository.save(application);
        return ApplicationResponse.from(saved);
    }

    // APP-02 (1단계) 자동배정 후보 조회. 신청을 생성하지 않는다.
    // [팀 합의 - 2단계 방식] 원래 API_SPEC은 매칭+신청 원샷이었으나,
    // 팀 합의로 "후보 조회 → 사용자 확인 후 신청"으로 변경했다. SPEC 문서 갱신 필요.
    @Transactional(readOnly = true)
    public ActivityResponse getAutoMatchCandidate(
            Long userId,
            BigDecimal latitude,
            BigDecimal longitude,
            List<Long> excludeActivityIds
    ) {
        List<Long> excludeFilter = (excludeActivityIds == null || excludeActivityIds.isEmpty())
                ? NO_EXCLUDE_FILTER
                : excludeActivityIds;

        List<CareActivity> candidates = careActivityRepository.findAutoMatchCandidates(userId, excludeFilter);

        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.NO_AUTO_MATCH_CANDIDATE);
        }

        boolean hasCoordinates = latitude != null && longitude != null;

        // 1순위: 거리 오름차순 랭크. 위치 미동의 시 전원 랭크 0으로 취급해
        // 사실상 2순위(lastCheckedAt)만으로 결정되게 한다.
        Map<Long, Integer> distanceRank = hasCoordinates
                ? competitionRank(candidates, activity -> GeoUtils.calculateDistanceKm(
                latitude, longitude,
                activity.getRecipient().getLatitude(),
                activity.getRecipient().getLongitude()
        ))
                : candidates.stream().collect(Collectors.toMap(CareActivity::getId, a -> 0));

        // 2순위: lastCheckedAt 오름차순 랭크(오래될수록 1등). null(안부 확인 이력 없음)은
        // 가장 오래된 것으로 간주해 최우선 순위를 준다.
        Map<Long, Integer> staleRank = competitionRank(candidates, activity -> {
            LocalDateTime lastCheckedAt = activity.getRecipient().getLastCheckedAt();
            return lastCheckedAt != null ? lastCheckedAt : LocalDateTime.MIN;
        });

        int bestScore = candidates.stream()
                .mapToInt(a -> distanceRank.get(a.getId()) + staleRank.get(a.getId()))
                .min()
                .orElseThrow();

        List<CareActivity> topTier = candidates.stream()
                .filter(a -> distanceRank.get(a.getId()) + staleRank.get(a.getId()) == bestScore)
                .collect(Collectors.toList());

        // 종합 순위가 동일한 후보군 중 랜덤으로 1건 선택
        CareActivity picked = topTier.get(ThreadLocalRandom.current().nextInt(topTier.size()));

        long approvedCount = activityApplicationRepository
                .countApprovedMap(List.of(picked.getId()))
                .getOrDefault(picked.getId(), 0L);

        return ActivityResponse.of(picked, approvedCount, latitude, longitude);
    }

    // 오름차순 기준 dense rank(동점은 같은 순위, 다음 순위는 건너뛰지 않고 이어짐)를 계산한다.
    private <T extends Comparable<T>> Map<Long, Integer> competitionRank(
            List<CareActivity> candidates,
            Function<CareActivity, T> keyExtractor
    ) {
        List<T> sortedDistinctKeys = candidates.stream()
                .map(keyExtractor)
                .distinct()
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .collect(Collectors.toList());

        Map<T, Integer> rankByKey = new HashMap<>();
        for (int i = 0; i < sortedDistinctKeys.size(); i++) {
            rankByKey.put(sortedDistinctKeys.get(i), i + 1);
        }

        return candidates.stream()
                .collect(Collectors.toMap(
                        CareActivity::getId,
                        activity -> rankByKey.get(keyExtractor.apply(activity))
                ));
    }

    // APP-02 (2단계) 자동배정 신청 확정. 후보 조회에서 받은 activityId로 실제 신청을 생성한다.
    @Transactional
    public ApplicationResponse applyAuto(Long activityId, Long userId) {
        CareActivity activity = findActivity(activityId);

        if (activity.getStatus() != ActivityStatus.RECRUITING
                && activity.getStatus() != ActivityStatus.READY) {
            throw new CustomException(ErrorCode.ACTIVITY_NOT_RECRUITING);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ActivityApplication application = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .orElse(null);

        if (application == null) {
            application = ActivityApplication.createAuto(activity, user);
        } else if (application.getStatus() == ApplicationStatus.CANCELED) {
            application.reactivate();
        } else {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }

        ActivityApplication saved = activityApplicationRepository.save(application);
        return ApplicationResponse.from(saved);
    }

    /**
     * APP-03 내 신청 목록 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> getMyApplications(
            Long userId,
            ApplicationStatus status,
            ApplicationType applicationType,
            Pageable pageable
    ) {
        Page<ActivityApplication> applicationPage = activityApplicationRepository.findMyApplications(
                userId,
                status != null, status,
                applicationType != null, applicationType,
                pageable
        );

        Page<ApplicationResponse> responsePage = applicationPage.map(ApplicationResponse::from);

        return PageResponse.from(responsePage);
    }

    /**
     * APP-04 내 활동 목록 조회 (APPROVED 신청 기준).
     */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> getMyActivities(
            Long userId,
            ActivityStatus activityStatus,
            Pageable pageable
    ) {
        Page<ActivityApplication> page = activityApplicationRepository.findMyActivities(
                userId, activityStatus != null, activityStatus, pageable
        );

        // 이번 페이지에 나온 활동들의 activityId를 모아 한 번에 activityRecordId를 조회한다.
        // (N+1 방지: 활동 개수만큼 반복 쿼리하지 않고 IN 절 한 번으로 처리)
        List<Long> activityIds = page.getContent().stream()
                .map(application -> application.getActivity().getId())
                .collect(Collectors.toList());

        Map<Long, Long> recordIdByActivityId = activityRecordRepository
                .findByActivity_IdIn(activityIds).stream()
                .collect(Collectors.toMap(
                        record -> record.getActivity().getId(),
                        ActivityRecord::getId
                ));

        Page<ApplicationResponse> responsePage = page.map(application ->
                ApplicationResponse.from(
                        application,
                        recordIdByActivityId.get(application.getActivity().getId())
                )
        );

        return PageResponse.from(responsePage);
    }

    /**
     * APP-05 신청 취소.
     * PENDING: 단순 취소.
     * APPROVED: 활동이 시작 전(RECRUITING/READY)인 경우만 허용.
     *           취소 후 승인 인원이 정원 미달이 되면 READY -> RECRUITING으로 되돌린다.
     * 그 외(REJECTED/CANCELED, 또는 활동이 이미 시작/종료됨): 취소 불가.
     */
    @Transactional
    public ApplicationResponse cancelApplication(Long applicationId, Long userId) {

        ActivityApplication application = activityApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (application.getStatus() == ApplicationStatus.PENDING) {
            application.cancel();
            return ApplicationResponse.from(application);
        }

        if (application.getStatus() == ApplicationStatus.APPROVED) {
            return cancelApprovedApplication(application);
        }

        throw new CustomException(ErrorCode.APPLICATION_NOT_CANCELABLE);
    }

    private ApplicationResponse cancelApprovedApplication(ActivityApplication application) {
        Long activityId = application.getActivity().getId();

        CareActivity activity = careActivityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (activity.getStatus() != ActivityStatus.RECRUITING
                && activity.getStatus() != ActivityStatus.READY) {
            throw new CustomException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        application.cancel();

        long remainingApproved = activityApplicationRepository
                .countApprovedMap(List.of(activityId))
                .getOrDefault(activityId, 0L);

        if (activity.getStatus() == ActivityStatus.READY
                && remainingApproved < activity.getRequiredPeople()) {
            activity.changeStatus(ActivityStatus.RECRUITING);
        }

        return ApplicationResponse.from(application);
    }

    private CareActivity findActivity(Long activityId) {
        return careActivityRepository.findDetailById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}