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
import com.dagachi.backend.common.ai.dto.AiActivityMatchingRequest;
import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiActivityMatchingResponse;
import com.dagachi.backend.user.application.dto.AutoMatchCandidateResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final ChecklistResponseRepository checklistResponseRepository;
    private final AiServiceClient aiServiceClient;

    private static final List<Long> NO_EXCLUDE_FILTER = List.of(-1L);
    // AI에 한 번에 전달할 최대 후보 수입니다.
    private static final int AI_CANDIDATE_LIMIT = 10;

    public ActivityApplicationService(
            ActivityApplicationRepository activityApplicationRepository,
            CareActivityRepository careActivityRepository,
            UserRepository userRepository,
            ActivityRecordRepository activityRecordRepository,
            ChecklistResponseRepository checklistResponseRepository,
            AiServiceClient aiServiceClient
    ) {
        this.activityApplicationRepository = activityApplicationRepository;
        this.careActivityRepository = careActivityRepository;
        this.userRepository = userRepository;
        this.activityRecordRepository = activityRecordRepository;
        this.checklistResponseRepository = checklistResponseRepository;
        this.aiServiceClient = aiServiceClient;
    }

    /**
     * APP-01 직접 신청.
     */
    @Transactional
    public ApplicationResponse applyDirect(Long activityId, Long userId) {

        CareActivity activity = findActivity(activityId);

        if (activity.getStatus() != ActivityStatus.RECRUITING) {
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

        long applicantCount = activityApplicationRepository
                .findActiveApplicationsByActivityIds(List.of(picked.getId()))
                .size();

        // 자동배정 후보는 findAutoMatchCandidates가 "내가 신청한 적 없는" 활동만
        // 걸러주므로 myApplicationStatus는 항상 null이다.
        return ActivityResponse.of(picked, approvedCount, applicantCount, latitude, longitude, null);
    }

    /**
     * AI 기반 APP-02 자동배정 후보 배치 조회.
     *
     * 기존 APP-02는 그대로 유지하고,
     * AI 매칭 화면에서 사용할 후보를 최대 10건 반환합니다.
     *
     * 흐름:
     * 1. 기존 APP-02 후보 조회
     * 2. 거리 + 안부 오래된 순위로 최대 10건 선별
     * 3. 사용자 경험 + 후보 정보를 FastAPI에 한 번 전달
     * 4. AI 순위대로 반환
     * 5. AI 장애 시 기존 규칙 기반 순서로 fallback
     */
    @Transactional(readOnly = true)
    public List<AutoMatchCandidateResponse> getAiAutoMatchCandidates(
            Long userId,
            BigDecimal latitude,
            BigDecimal longitude,
            List<Long> excludeActivityIds
    ) {
        List<Long> excludeFilter =
                (excludeActivityIds == null || excludeActivityIds.isEmpty())
                        ? NO_EXCLUDE_FILTER
                        : excludeActivityIds;

        List<CareActivity> candidates =
                careActivityRepository.findAutoMatchCandidates(
                        userId,
                        excludeFilter
                );

        if (candidates.isEmpty()) {
            throw new CustomException(
                    ErrorCode.NO_AUTO_MATCH_CANDIDATE
            );
        }

        // 기존 거리 + 안부 기준으로
        // AI에 전달할 후보를 최대 10건까지 선별합니다.
        List<CareActivity> selectedCandidates =
                selectAiCandidates(
                        candidates,
                        latitude,
                        longitude
                );

        AiActivityMatchingRequest request =
                new AiActivityMatchingRequest(
                        buildUserMatchingProfile(userId),
                        buildAiMatchingCandidates(
                                selectedCandidates,
                                latitude,
                                longitude
                        )
                );

        AiActivityMatchingResponse aiResponse;

        try {
            aiResponse =
                    aiServiceClient.matchActivities(request);

        } catch (CustomException exception) {

            /*
             * AI Service 장애 / timeout / 잘못된 응답이면
             * 추천 기능 자체를 실패시키지 않고,
             * 이미 선별된 기존 규칙 기반 후보 순서를 사용합니다.
             */
            return buildAutoMatchCandidateResponses(
                    selectedCandidates,
                    latitude,
                    longitude,
                    Map.of(),
                    null
            );
        }

        Map<Long, Integer> rankByActivityId =
                new HashMap<>();

        Map<Long, String> reasonByActivityId =
                new HashMap<>();

        for (AiActivityMatchingResponse.Recommendation recommendation
                : aiResponse.recommendations()) {

            rankByActivityId.put(
                    recommendation.activityId(),
                    recommendation.rank()
            );

            reasonByActivityId.put(
                    recommendation.activityId(),
                    recommendation.reason()
            );
        }

        // AI가 반환한 rank 순서대로 후보를 정렬합니다.
        List<CareActivity> aiOrderedCandidates =
                new ArrayList<>(selectedCandidates);

        aiOrderedCandidates.sort(
                Comparator.comparingInt(
                        activity ->
                                rankByActivityId.getOrDefault(
                                        activity.getId(),
                                        Integer.MAX_VALUE
                                )
                )
        );

        return buildAutoMatchCandidateResponses(
                aiOrderedCandidates,
                latitude,
                longitude,
                reasonByActivityId,
                aiResponse.model()
        );
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

    /**
     * AI 활동 매칭에 사용할 로그인 사용자의 활동 경험을 만듭니다.
     *
     * 기관 검토가 승인된 MET 활동만 실제 완료 경험으로 사용합니다.
     */
    private AiActivityMatchingRequest.Profile buildUserMatchingProfile(
            Long userId
    ) {
        long completedActivityCount =
                activityApplicationRepository.countCompletedCareChecks(
                        userId,
                        ApplicationStatus.APPROVED,
                        ActivityReviewStatus.APPROVED,
                        VisitResult.MET
                );

        List<ActivityRecord> completedRecords =
                activityRecordRepository.findApprovedMetRecordsByUserId(userId);

        List<String> experiencedRegions = new ArrayList<>();
        List<String> experiencedAgeGroups = new ArrayList<>();

        for (ActivityRecord record : completedRecords) {
            CareRecipient recipient = record.getActivity().getRecipient();

            String district = extractDistrict(recipient.getAddress());

            if (!"지역 정보 없음".equals(district)
                    && !experiencedRegions.contains(district)) {
                experiencedRegions.add(district);
            }

            String ageGroup =
                    AddressUtils.calculateAgeGroup(recipient.getBirthYear());

            if (!"연령 정보 없음".equals(ageGroup)
                    && !experiencedAgeGroups.contains(ageGroup)) {
                experiencedAgeGroups.add(ageGroup);
            }
        }

        return new AiActivityMatchingRequest.Profile(
                completedActivityCount,
                experiencedRegions,
                experiencedAgeGroups
        );
    }


    /**
     * AI 매칭에서는 화면 표시용 동/도로명이 아니라
     * 구·군 단위 지역을 사용합니다.
     */
    private String extractDistrict(String address) {

        if (address == null || address.isBlank()) {
            return "지역 정보 없음";
        }

        String[] addressParts = address.trim().split("\\s+");

        for (String part : addressParts) {
            if (part.endsWith("구") || part.endsWith("군")) {
                return part;
            }
        }

        return "지역 정보 없음";
    }

    /**
     * 기존 APP-02의 거리 + 안부 오래된 순위를 이용해
     * AI에 전달할 후보를 최대 10명까지 선별합니다.
     *
     * 기존 자동배정 로직은 수정하지 않고,
     * AI 매칭에서만 사용하는 1차 후보 압축용 메서드입니다.
     */
    private List<CareActivity> selectAiCandidates(
            List<CareActivity> candidates,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        boolean hasCoordinates =
                latitude != null && longitude != null;

        Map<Long, Integer> distanceRank;

        if (hasCoordinates) {
            distanceRank = competitionRank(
                    candidates,
                    activity -> GeoUtils.calculateDistanceKm(
                            latitude,
                            longitude,
                            activity.getRecipient().getLatitude(),
                            activity.getRecipient().getLongitude()
                    )
            );
        } else {
            distanceRank = candidates.stream()
                    .collect(Collectors.toMap(
                            CareActivity::getId,
                            activity -> 0
                    ));
        }

        Map<Long, Integer> staleRank =
                competitionRank(candidates, activity -> {
                    LocalDateTime lastCheckedAt =
                            activity.getRecipient().getLastCheckedAt();

                    return lastCheckedAt != null
                            ? lastCheckedAt
                            : LocalDateTime.MIN;
                });

        List<CareActivity> sortedCandidates =
                new ArrayList<>(candidates);

        sortedCandidates.sort(
                Comparator
                        .comparingInt(
                                (CareActivity activity) ->
                                        distanceRank.get(activity.getId())
                                                + staleRank.get(activity.getId())
                        )
                        .thenComparing(CareActivity::getId)
        );

        int endIndex =
                Math.min(
                        AI_CANDIDATE_LIMIT,
                        sortedCandidates.size()
                );

        return new ArrayList<>(
                sortedCandidates.subList(0, endIndex)
        );
    }

    /**
     * AI 후보 대상자들의 최근 기관 승인 활동기록을 만듭니다.
     *
     * 대상자별 최근 기록을 최대 3건 사용하고,
     * MET 기록은 ChecklistItem.code -> selectedValue 형태의
     * 체크리스트를 함께 전달합니다.
     *
     * NOT_MET 기록은 체크리스트를 전달하지 않습니다.
     * specialNote는 AI 매칭 정보에 포함하지 않습니다.
     */
    private Map<Long, List<AiActivityMatchingRequest.RecentRecord>>
    buildRecentRecordsByRecipient(
            List<CareActivity> candidates
    ) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        // AI 후보들의 대상자 ID를 중복 없이 모읍니다.
        List<Long> recipientIds = new ArrayList<>();

        for (CareActivity candidate : candidates) {
            Long recipientId = candidate.getRecipient().getId();

            if (!recipientIds.contains(recipientId)) {
                recipientIds.add(recipientId);
            }
        }

        // 기관 검토가 APPROVED인 과거 기록을
        // 대상자별 최신 completedAt 순으로 한 번에 조회합니다.
        List<ActivityRecord> approvedRecords =
                activityRecordRepository
                        .findApprovedRecordsByRecipientIds(recipientIds);

        // 대상자마다 최근 3건까지만 선택합니다.
        Map<Long, List<ActivityRecord>> recordsByRecipient =
                new HashMap<>();

        List<ActivityRecord> selectedRecords =
                new ArrayList<>();

        for (ActivityRecord record : approvedRecords) {
            Long recipientId =
                    record.getActivity()
                            .getRecipient()
                            .getId();

            List<ActivityRecord> recipientRecords =
                    recordsByRecipient.computeIfAbsent(
                            recipientId,
                            key -> new ArrayList<>()
                    );

            if (recipientRecords.size() >= 3) {
                continue;
            }

            recipientRecords.add(record);
            selectedRecords.add(record);
        }

        // 선택된 최근 기록들의 ID를 모읍니다.
        List<Long> recordIds = new ArrayList<>();

        for (ActivityRecord record : selectedRecords) {
            recordIds.add(record.getId());
        }

        // 기록이 하나도 없다면 후보별 최근 기록도 없는 상태입니다.
        if (recordIds.isEmpty()) {
            return Map.of();
        }

        // 최근 기록들의 체크리스트 응답을 한 번에 조회합니다.
        List<ChecklistResponse> checklistResponses =
                checklistResponseRepository
                        .findByActivityRecordIdsWithItems(recordIds);

        // ActivityRecord ID별
        // ChecklistItem.code -> selectedValue 형태로 정리합니다.
        Map<Long, Map<String, String>> checklistByRecordId =
                new HashMap<>();

        for (ChecklistResponse response : checklistResponses) {

            String selectedValue = response.getSelectedValue();

            if (selectedValue == null || selectedValue.isBlank()) {
                continue;
            }

            Long recordId =
                    response.getActivityRecord().getId();

            Map<String, String> checklist =
                    checklistByRecordId.computeIfAbsent(
                            recordId,
                            key -> new HashMap<>()
                    );

            checklist.put(
                    response.getChecklistItem().getCode(),
                    selectedValue
            );
        }

        Map<Long, List<AiActivityMatchingRequest.RecentRecord>>
                recentRecordsByRecipient = new HashMap<>();

        LocalDate today = LocalDate.now();

        for (Map.Entry<Long, List<ActivityRecord>> entry
                : recordsByRecipient.entrySet()) {

            List<AiActivityMatchingRequest.RecentRecord> recentRecords =
                    new ArrayList<>();

            for (ActivityRecord record : entry.getValue()) {

                // APPROVED 기록이라면 정상적으로 존재해야 하지만,
                // AI 요청을 깨뜨리지 않도록 비정상 데이터는 제외합니다.
                if (record.getCompletedAt() == null
                        || record.getVisitResult() == null) {
                    continue;
                }

                long calculatedDaysAgo =
                        ChronoUnit.DAYS.between(
                                record.getCompletedAt().toLocalDate(),
                                today
                        );

                int daysAgo =
                        (int) Math.max(0, calculatedDaysAgo);

                Map<String, String> checklist = null;

                if (record.getVisitResult() == VisitResult.MET) {
                    checklist =
                            checklistByRecordId.getOrDefault(
                                    record.getId(),
                                    Map.of()
                            );
                }

                recentRecords.add(
                        new AiActivityMatchingRequest.RecentRecord(
                                daysAgo,
                                record.getVisitResult().name(),
                                checklist
                        )
                );
            }

            recentRecordsByRecipient.put(
                    entry.getKey(),
                    recentRecords
            );
        }

        return recentRecordsByRecipient;
    }

    /**
     * AI에 전달할 활동 후보 정보를 만듭니다.
     *
     * 거리, 최근 안부 확인 시점, 대상자 연령대/지역,
     * 모집 인원, 승인 인원, 최근 승인 활동기록을 함께 구성합니다.
     */
    private List<AiActivityMatchingRequest.Candidate>
    buildAiMatchingCandidates(
            List<CareActivity> candidates,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        List<Long> activityIds = new ArrayList<>();

        for (CareActivity candidate : candidates) {
            activityIds.add(candidate.getId());
        }

        Map<Long, Long> approvedCountMap =
                activityApplicationRepository
                        .countApprovedMap(activityIds);

        Map<Long, List<AiActivityMatchingRequest.RecentRecord>>
                recentRecordsByRecipient =
                buildRecentRecordsByRecipient(candidates);

        List<AiActivityMatchingRequest.Candidate> aiCandidates =
                new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (CareActivity candidate : candidates) {

            CareRecipient recipient = candidate.getRecipient();

            BigDecimal calculatedDistance =
                    GeoUtils.calculateDistanceKm(
                            latitude,
                            longitude,
                            recipient.getLatitude(),
                            recipient.getLongitude()
                    );

            Double distanceKm =
                    calculatedDistance != null
                            ? calculatedDistance.doubleValue()
                            : null;

            Integer daysSinceLastChecked = null;

            if (recipient.getLastCheckedAt() != null) {
                long calculatedDays =
                        ChronoUnit.DAYS.between(
                                recipient.getLastCheckedAt().toLocalDate(),
                                today
                        );

                daysSinceLastChecked =
                        (int) Math.max(0, calculatedDays);
            }

            List<AiActivityMatchingRequest.RecentRecord> recentRecords =
                    recentRecordsByRecipient.getOrDefault(
                            recipient.getId(),
                            List.of()
                    );

            aiCandidates.add(
                    new AiActivityMatchingRequest.Candidate(
                            candidate.getId(),
                            distanceKm,
                            daysSinceLastChecked,
                            AddressUtils.calculateAgeGroup(
                                    recipient.getBirthYear()
                            ),
                            extractDistrict(
                                    recipient.getAddress()
                            ),
                            candidate.getRequiredPeople(),
                            approvedCountMap.getOrDefault(
                                    candidate.getId(),
                                    0L
                            ),
                            recentRecords
                    )
            );
        }

        return aiCandidates;
    }

    /**
     * 선별된 활동들을 APP-02 AI 배치 응답으로 변환합니다.
     *
     * AI 성공 시 reason/model을 포함하고,
     * fallback 시에는 둘 다 null로 반환합니다.
     */
    private List<AutoMatchCandidateResponse>
    buildAutoMatchCandidateResponses(
            List<CareActivity> candidates,
            BigDecimal latitude,
            BigDecimal longitude,
            Map<Long, String> reasonByActivityId,
            String model
    ) {
        List<Long> activityIds = new ArrayList<>();

        for (CareActivity candidate : candidates) {
            activityIds.add(candidate.getId());
        }

        Map<Long, Long> approvedCountMap =
                activityApplicationRepository
                        .countApprovedMap(activityIds);

        List<ActivityApplication> activeApplications =
                activityApplicationRepository
                        .findActiveApplicationsByActivityIds(
                                activityIds
                        );

        Map<Long, Long> applicantCountMap =
                new HashMap<>();

        for (ActivityApplication application
                : activeApplications) {

            Long activityId =
                    application.getActivity().getId();

            applicantCountMap.merge(
                    activityId,
                    1L,
                    Long::sum
            );
        }

        List<AutoMatchCandidateResponse> responses =
                new ArrayList<>();

        for (CareActivity candidate : candidates) {

            Long activityId = candidate.getId();

            responses.add(
                    AutoMatchCandidateResponse.of(
                            candidate,
                            approvedCountMap.getOrDefault(
                                    activityId,
                                    0L
                            ),
                            applicantCountMap.getOrDefault(
                                    activityId,
                                    0L
                            ),
                            latitude,
                            longitude,
                            reasonByActivityId.get(activityId),
                            model
                    )
            );
        }

        return responses;
    }

    // APP-02 (2단계) 자동배정 신청 확정. 후보 조회에서 받은 activityId로 실제 신청을 생성한다.
    @Transactional
    public ApplicationResponse applyAuto(Long activityId, Long userId) {
        CareActivity activity = findActivity(activityId);

        if (activity.getStatus() != ActivityStatus.RECRUITING) {
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