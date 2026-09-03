package com.dagachi.backend.institution.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.InstitutionActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityApplicationResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityCreateRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityDetailResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityStatusRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivitySummaryResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityUpdateRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityApplicationRejectRequest;
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

    private final CareRecipientRepository
            careRecipientRepository;

    public InstitutionActivityService(
            UserRepository userRepository,
            InstitutionActivityRepository institutionActivityRepository,
            ActivityApplicationRepository activityApplicationRepository,
            CareRecipientRepository careRecipientRepository
    ) {
        this.userRepository =
                userRepository;

        this.institutionActivityRepository =
                institutionActivityRepository;

        this.activityApplicationRepository =
                activityApplicationRepository;

        this.careRecipientRepository =
                careRecipientRepository;
    }

    /**
     * 기관 활동 목록 조회.
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
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        validateDateRange(
                dateFrom,
                dateTo
        );

        boolean hasStatus =
                status != null;

        boolean hasRecipient =
                recipientId != null;

        ActivityStatus normalizedStatus =
                hasStatus
                        ? status
                        : ActivityStatus.RECRUITING;

        Long normalizedRecipientId =
                hasRecipient
                        ? recipientId
                        : -1L;

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

        List<Long> activityIds =
                activityPage
                        .getContent()
                        .stream()
                        .map(CareActivity::getId)
                        .toList();

        Map<Long, Long> approvedCountMap =
                activityIds.isEmpty()
                        ? Map.of()
                        : activityApplicationRepository
                          .countApprovedMap(
                                  activityIds
                          );

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

        return PageResponse.from(
                responsePage
        );
    }

    /**
     * 기관 활동 상세 조회.
     */
    @Transactional(readOnly = true)
    public InstitutionActivityDetailResponse
    getInstitutionActivity(
            Long userId,
            Long activityId
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        return createDetailResponse(
                activity
        );
    }

    /**
     * 기관 활동 등록.
     */
    @Transactional
    public InstitutionActivityDetailResponse
    createInstitutionActivity(
            Long userId,
            InstitutionActivityCreateRequest request
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareRecipient recipient =
                careRecipientRepository
                        .findByIdAndInstitution_IdAndDeletedFalse(
                                request.recipientId(),
                                institution.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.RESOURCE_NOT_FOUND
                                        )
                        );

        /*
         * 관리가 종료된 대상자에게는
         * 새로운 활동을 등록할 수 없다.
         */
        if (
                recipient.getStatus()
                        != CareRecipientStatus.ACTIVE
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        institution,
                        user,
                        request.scheduledAt(),
                        request.requiredPeople(),
                        request.genderCondition()
                );

        CareActivity savedActivity =
                institutionActivityRepository.save(
                        activity
                );

        /*
         * 새 활동에는 신청자와 활동 결과가 없다.
         */
        return InstitutionActivityDetailResponse.from(
                savedActivity,
                0L,
                0L,
                null
        );
    }

    /**
     * 기관 활동 정보 수정.
     */
    @Transactional
    public InstitutionActivityDetailResponse
    updateInstitutionActivity(
            Long userId,
            Long activityId,
            InstitutionActivityUpdateRequest request
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        validateEditableStatus(
                activity.getStatus()
        );

        long approvedCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.APPROVED
                        );

        /*
         * 승인 인원보다 필요 인원을
         * 작게 설정할 수 없다.
         */
        if (
                request.requiredPeople()
                        < approvedCount
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        activity.updateInformation(
                request.scheduledAt(),
                request.requiredPeople()
        );

        return createDetailResponse(
                activity
        );
    }

    /**
     * 기관 활동 상태 변경.
     */
    @Transactional
    public InstitutionActivityDetailResponse
    changeInstitutionActivityStatus(
            Long userId,
            Long activityId,
            InstitutionActivityStatusRequest request
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        ActivityStatus currentStatus =
                activity.getStatus();

        ActivityStatus newStatus =
                request.status();

        validateStatusChange(
                currentStatus,
                newStatus
        );

        /*
         * 필요한 인원이 모두 승인된 경우에만
         * READY 상태로 변경할 수 있다.
         */
        if (newStatus == ActivityStatus.READY) {
            long approvedCount =
                    institutionActivityRepository
                            .countApplications(
                                    activityId,
                                    ApplicationStatus.APPROVED
                            );

            if (
                    approvedCount
                            < activity.getRequiredPeople()
            ) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT_VALUE
                );
            }
        }

        activity.changeStatus(
                newStatus
        );

        return createDetailResponse(
                activity
        );
    }

    /**
     * 기관 활동 신청자 목록 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionActivityApplicationResponse>
    getInstitutionActivityApplications(
            Long userId,
            Long activityId,
            ApplicationStatus status,
            Pageable pageable
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        /*
         * 해당 기관의 활동인지 먼저 확인한다.
         */
        findInstitutionActivity(
                institution.getId(),
                activityId
        );

        boolean hasStatus =
                status != null;

        ApplicationStatus normalizedStatus =
                hasStatus
                        ? status
                        : ApplicationStatus.PENDING;

        Page<ActivityApplication> applicationPage =
                institutionActivityRepository
                        .findActivityApplications(
                                institution.getId(),
                                activityId,
                                hasStatus,
                                normalizedStatus,
                                pageable
                        );

        Page<InstitutionActivityApplicationResponse> responsePage =
                applicationPage.map(
                        InstitutionActivityApplicationResponse::from
                );

        return PageResponse.from(
                responsePage
        );
    }

    /**
     * 활동 상세 응답을 생성한다.
     */
    private InstitutionActivityDetailResponse
    createDetailResponse(
            CareActivity activity
    ) {
        Long activityId =
                activity.getId();

        long approvedCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.APPROVED
                        );

        long pendingCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.PENDING
                        );

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
     * 로그인 담당자의 기관에 속한 활동을 조회한다.
     */
    private CareActivity findInstitutionActivity(
            Long institutionId,
            Long activityId
    ) {
        return institutionActivityRepository
                .findDetailActivity(
                        institutionId,
                        activityId
                )
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
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
     * 목록 조회 기간을 검사한다.
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

    /**
     * 활동 정보를 수정할 수 있는 상태인지 검사한다.
     */
    private void validateEditableStatus(
            ActivityStatus status
    ) {
        if (
                status != ActivityStatus.RECRUITING
                        && status != ActivityStatus.READY
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    /**
     * 허용된 활동 상태 변경인지 검사한다.
     */
    private void validateStatusChange(
            ActivityStatus currentStatus,
            ActivityStatus newStatus
    ) {
        boolean allowed =
                switch (currentStatus) {
                    case RECRUITING ->
                            newStatus == ActivityStatus.READY
                                    || newStatus == ActivityStatus.CANCELED;

                    case READY ->
                            newStatus == ActivityStatus.RECRUITING
                                    || newStatus == ActivityStatus.IN_PROGRESS
                                    || newStatus == ActivityStatus.CANCELED;

                    case IN_PROGRESS ->
                            newStatus == ActivityStatus.COMPLETED;

                    case COMPLETED, CANCELED ->
                            false;
                };

        if (!allowed) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
    /**
     * 기관 담당자가 봉사 신청을 승인한다.
     */
    @Transactional
    public InstitutionActivityApplicationResponse
    approveActivityApplication(
            Long userId,
            Long activityId,
            Long applicationId
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        /*
         * 모집 중인 활동의 신청만 승인할 수 있다.
         */
        if (
                activity.getStatus()
                        != ActivityStatus.RECRUITING
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        ActivityApplication application =
                findActivityApplication(
                        institution.getId(),
                        activityId,
                        applicationId
                );

        /*
         * 승인 대기 상태의 신청만 처리할 수 있다.
         */
        validatePendingApplication(
                application
        );

        long approvedCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.APPROVED
                        );

        /*
         * 필요한 인원이 이미 모두 승인됐다면
         * 추가 신청을 승인할 수 없다.
         */
        if (
                approvedCount
                        >= activity.getRequiredPeople()
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        application.approve(
                user
        );

        return InstitutionActivityApplicationResponse.from(
                application
        );
    }
    /**
     * 기관 담당자가 봉사 신청을 반려한다.
     */
    @Transactional
    public InstitutionActivityApplicationResponse
    rejectActivityApplication(
            Long userId,
            Long activityId,
            Long applicationId,
            InstitutionActivityApplicationRejectRequest request
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        /*
         * 모집 중인 활동의 신청만 반려할 수 있다.
         */
        if (
                activity.getStatus()
                        != ActivityStatus.RECRUITING
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        ActivityApplication application =
                findActivityApplication(
                        institution.getId(),
                        activityId,
                        applicationId
                );

        validatePendingApplication(
                application
        );

        application.reject(
                user,
                request.reason().trim()
        );

        return InstitutionActivityApplicationResponse.from(
                application
        );
    }
    /**
     * 기관과 활동에 속한 신청서를 조회한다.
     */
    private ActivityApplication
    findActivityApplication(
            Long institutionId,
            Long activityId,
            Long applicationId
    ) {
        return institutionActivityRepository
                .findActivityApplication(
                        institutionId,
                        activityId,
                        applicationId
                )
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
                );
    }

    /**
     * 승인 대기 상태의 신청인지 검사한다.
     */
    private void validatePendingApplication(
            ActivityApplication application
    ) {
        if (
                application.getStatus()
                        != ApplicationStatus.PENDING
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}