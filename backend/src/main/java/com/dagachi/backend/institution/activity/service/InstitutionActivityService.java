package com.dagachi.backend.institution.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.InstitutionActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityDetailResponse;
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
 * 기관 활동 관리 기능을 처리하는 Service.
 */
@Service
public class InstitutionActivityService {

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
     * ACT-04 기관 활동 목록 조회.
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
        // 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 담당자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 시작일이 종료일보다 늦으면 잘못된 요청으로 처리
        validateDateRange(
                dateFrom,
                dateTo
        );

        /*
         * 필터가 사용됐는지 별도의 값으로 전달한다.
         *
         * PostgreSQL에서 null 값의 자료형을 판단하지 못해
         * 오류가 발생하는 것을 방지하기 위한 처리다.
         */
        boolean hasStatus =
                status != null;

        boolean hasRecipient =
                recipientId != null;

        /*
         * 필터를 사용하지 않더라도 쿼리에는
         * null이 아닌 임시 값을 전달한다.
         *
         * hasStatus 또는 hasRecipient가 false이면
         * 실제 조회 조건에는 적용되지 않는다.
         */
        ActivityStatus normalizedStatus =
                hasStatus
                        ? status
                        : ActivityStatus.RECRUITING;

        Long normalizedRecipientId =
                hasRecipient
                        ? recipientId
                        : -1L;

        /*
         * 시작일이 없으면 아주 과거부터 조회한다.
         * 시작일이 있으면 해당 날짜의 00시부터 조회한다.
         */
        LocalDateTime normalizedDateFrom =
                dateFrom == null
                        ? LocalDateTime.of(
                        1970,
                        1,
                        1,
                        0,
                        0
                )
                        : dateFrom.atStartOfDay();

        /*
         * 종료일이 없으면 먼 미래까지 조회한다.
         *
         * 종료일이 있다면 그다음 날 00시 미만으로 조회하여
         * 사용자가 입력한 종료일 전체를 포함한다.
         */
        LocalDateTime normalizedDateTo =
                dateTo == null
                        ? LocalDateTime.of(
                        9999,
                        12,
                        31,
                        0,
                        0
                )
                        : dateTo
                          .plusDays(1)
                          .atStartOfDay();

        // 해당 기관의 활동 목록 조회
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

        // 현재 페이지에 포함된 활동 번호 수집
        List<Long> activityIds =
                activityPage
                        .getContent()
                        .stream()
                        .map(CareActivity::getId)
                        .toList();

        /*
         * 각 활동의 승인 인원을 한 번에 조회한다.
         *
         * 활동이 없을 때 IN () 형태의 잘못된 쿼리가
         * 실행되지 않도록 빈 Map을 사용한다.
         */
        Map<Long, Long> approvedCountMap =
                activityIds.isEmpty()
                        ? Map.of()
                        : activityApplicationRepository
                          .countApprovedMap(
                                  activityIds
                          );

        // Entity를 목록 응답 DTO로 변환
        Page<InstitutionActivitySummaryResponse> responsePage =
                activityPage.map(
                        activity ->
                                InstitutionActivitySummaryResponse.of(
                                        activity,
                                        approvedCountMap
                                                .getOrDefault(
                                                        activity.getId(),
                                                        0L
                                                )
                                )
                );

        return PageResponse.from(
                responsePage
        );
    }

    /**
     * ACT-05 기관 활동 상세 조회.
     */
    @Transactional(readOnly = true)
    public InstitutionActivityDetailResponse
    getInstitutionActivity(
            Long userId,
            Long activityId
    ) {
        // 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 담당자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        /*
         * 활동 번호와 기관 번호를 함께 사용한다.
         *
         * 다른 기관의 활동이거나 존재하지 않는 활동이면
         * RESOURCE_NOT_FOUND 예외가 발생한다.
         */
        CareActivity activity =
                institutionActivityRepository
                        .findDetailActivity(
                                institution.getId(),
                                activityId
                        )
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.RESOURCE_NOT_FOUND
                                        )
                        );

        // 승인된 신청 인원
        long approvedCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.APPROVED
                        );

        // 승인 대기 중인 신청 인원
        long pendingCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.PENDING
                        );

        /*
         * 활동 결과 기록 조회.
         *
         * 아직 결과가 없으면 null을 사용하고
         * DTO에서 hasRecord를 false로 변환한다.
         */
        ActivityRecord record =
                institutionActivityRepository
                        .findActivityRecord(
                                activityId
                        )
                        .orElse(null);

        return InstitutionActivityDetailResponse.from(
                activity,
                approvedCount,
                pendingCount,
                record
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
     * 활동 조회 시작일과 종료일을 검사한다.
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