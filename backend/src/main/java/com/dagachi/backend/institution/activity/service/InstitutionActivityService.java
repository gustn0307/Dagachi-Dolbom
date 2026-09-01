package com.dagachi.backend.institution.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.InstitutionActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.activity.dto.InstitutionActivitySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 기관 담당자의 활동 관리 기능을 처리하는 Service.
 */
@Service
public class InstitutionActivityService {

    /**
     * 날짜 필터가 없을 때 사용하는 기본 조회 시작일.
     */
    private static final LocalDateTime MIN_DATE =
            LocalDateTime.of(
                    2000,
                    1,
                    1,
                    0,
                    0
            );

    /**
     * 날짜 필터가 없을 때 사용하는 기본 조회 종료일.
     */
    private static final LocalDateTime MAX_DATE =
            LocalDateTime.of(
                    2100,
                    1,
                    1,
                    0,
                    0
            );

    private final UserRepository userRepository;

    private final InstitutionActivityRepository
            institutionActivityRepository;

    private final ActivityApplicationRepository
            activityApplicationRepository;

    public InstitutionActivityService(
            UserRepository userRepository,
            InstitutionActivityRepository institutionActivityRepository,
            ActivityApplicationRepository activityApplicationRepository
    ) {
        this.userRepository =
                userRepository;

        this.institutionActivityRepository =
                institutionActivityRepository;

        this.activityApplicationRepository =
                activityApplicationRepository;
    }

    /**
     * ACT-04 기관 활동 목록을 조회한다.
     *
     * 로그인 담당자의 소속 기관 활동만 조회하며,
     * 상태·대상자·기간 필터를 지원한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionActivitySummaryResponse>
    getInstitutionActivities(
            Long userId,
            ActivityStatus status,
            Long recipientId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 시작일과 종료일 순서 검증
        validateDateRange(
                dateFrom,
                dateTo
        );

        /*
         * 상태값이 있으면 상태 필터를 적용한다.
         *
         * 상태값이 없을 때도 Repository 파라미터가
         * null이 되지 않도록 RECRUITING을 기본값으로 전달한다.
         * hasStatus가 false이므로 실제 필터에는 적용되지 않는다.
         */
        boolean hasStatus =
                status != null;

        ActivityStatus normalizedStatus =
                hasStatus
                        ? status
                        : ActivityStatus.RECRUITING;

        /*
         * 대상자 ID가 있으면 대상자 필터를 적용한다.
         *
         * 값이 없을 때는 실제 ID와 겹치지 않는 -1을 전달한다.
         */
        boolean hasRecipient =
                recipientId != null;

        Long normalizedRecipientId =
                hasRecipient
                        ? recipientId
                        : -1L;

        // 시작일이 있으면 해당 날짜 00시부터 조회한다.
        LocalDateTime normalizedDateFrom =
                dateFrom != null
                        ? dateFrom.atStartOfDay()
                        : MIN_DATE;

        /*
         * 종료일을 포함하기 위해 다음 날 00시 미만으로 조회한다.
         *
         * 예:
         * dateTo = 2026-09-05
         * 실제 조건 = 2026-09-06 00:00 미만
         */
        LocalDateTime normalizedDateTo =
                dateTo != null
                        ? dateTo
                          .plusDays(1)
                          .atStartOfDay()
                        : MAX_DATE;

        // 로그인 담당자의 기관 활동 목록 조회
        Page<CareActivity> activityPage =
                institutionActivityRepository
                        .findInstitutionActivities(
                                institution.getId(),
                                hasStatus,
                                normalizedStatus,
                                hasRecipient,
                                normalizedRecipientId,
                                normalizedDateFrom,
                                normalizedDateTo,
                                pageable
                        );

        /*
         * 현재 페이지에 활동이 없다면
         * 승인 인원 조회 Query를 실행하지 않는다.
         */
        Map<Long, Long> approvedCountMap =
                getApprovedCountMap(
                        activityPage.getContent()
                );

        /*
         * CareActivity Entity를 기관 활동 목록 DTO로 변환한다.
         *
         * 활동 ID를 기준으로 승인 인원수를 함께 넣는다.
         */
        Page<InstitutionActivitySummaryResponse> responsePage =
                activityPage.map(
                        activity ->
                                InstitutionActivitySummaryResponse.of(
                                        activity,
                                        approvedCountMap.getOrDefault(
                                                activity.getId(),
                                                0L
                                        )
                                )
                );

        // 공통 페이지 응답으로 변환
        return PageResponse.from(
                responsePage
        );
    }

    /**
     * 활동 목록에 포함된 활동 ID를 이용해
     * APPROVED 상태의 신청 인원을 한 번에 조회한다.
     */
    private Map<Long, Long> getApprovedCountMap(
            List<CareActivity> activities
    ) {
        if (activities.isEmpty()) {
            return Map.of();
        }

        List<Long> activityIds =
                activities.stream()
                        .map(CareActivity::getId)
                        .toList();

        return activityApplicationRepository
                .countApprovedMap(
                        activityIds
                );
    }

    /**
     * 삭제되지 않은 로그인 사용자를 조회한다.
     */
    private User findUser(
            Long userId
    ) {
        return userRepository
                .findByIdAndDeletedFalse(
                        userId
                )
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.USER_NOT_FOUND
                                )
                );
    }

    /**
     * 로그인 사용자의 소속 기관을 확인한다.
     */
    private Institution getInstitution(
            User user
    ) {
        Institution institution =
                user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        return institution;
    }

    /**
     * 시작일이 종료일보다 늦으면 잘못된 요청으로 처리한다.
     */
    private void validateDateRange(
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (
                dateFrom != null
                        && dateTo != null
                        && dateFrom.isAfter(dateTo)
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}