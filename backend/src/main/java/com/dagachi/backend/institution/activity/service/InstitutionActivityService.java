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
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.InstitutionActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityRecordRevisionRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityRecordResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityApplicationResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityCreateRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityDetailResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityStatusRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivitySummaryResponse;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityUpdateRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityApplicationRejectRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionPendingApplicationSummaryResponse;
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

    private final ChecklistResponseRepository
            checklistResponseRepository;

    private final UserRepository
            userRepository;

    private final InstitutionActivityRepository
            institutionActivityRepository;

    private final ActivityApplicationRepository
            activityApplicationRepository;

    private final CareActivityRepository
            careActivityRepository;

    private final CareRecipientRepository
            careRecipientRepository;

    public InstitutionActivityService(
            UserRepository userRepository,
            InstitutionActivityRepository institutionActivityRepository,
            ActivityApplicationRepository activityApplicationRepository,
            CareActivityRepository careActivityRepository,
            CareRecipientRepository careRecipientRepository,
            ChecklistResponseRepository checklistResponseRepository
    ) {
        this.userRepository =
                userRepository;

        this.institutionActivityRepository =
                institutionActivityRepository;

        this.activityApplicationRepository =
                activityApplicationRepository;

        this.careActivityRepository =
                careActivityRepository;

        this.careRecipientRepository =
                careRecipientRepository;

        this.checklistResponseRepository =
                checklistResponseRepository;
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
    /**
     * 기관 담당자가 봉사 신청을 승인한다.
     *
     * 승인된 인원이 필요한 모집 인원에 도달하면
     * 활동 상태를 자동으로 READY로 변경한다.
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

        /*
         * 다른 기관의 활동에 접근하지 못하도록
         * 로그인 기관 소유의 활동인지 확인한다.
         */
        findInstitutionActivity(
                institution.getId(),
                activityId
        );

        /*
         * 여러 승인 요청이 동시에 들어와도
         * 모집 인원을 초과하지 않도록 활동을 잠근다.
         */
        CareActivity activity =
                careActivityRepository
                        .findByIdForUpdate(
                                activityId
                        )
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.RESOURCE_NOT_FOUND
                                        )
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
         * 승인 대기 상태인 신청만 승인할 수 있다.
         */
        validatePendingApplication(
                application
        );

        /*
         * 현재 승인된 봉사자 수를 조회한다.
         */
        long approvedCount =
                institutionActivityRepository
                        .countApplications(
                                activityId,
                                ApplicationStatus.APPROVED
                        );

        /*
         * 이미 필요한 인원을 모두 승인했다면
         * 추가 승인을 막는다.
         */
        if (
                approvedCount
                        >= activity.getRequiredPeople()
        ) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        /*
         * 봉사 신청을 승인한다.
         */
        application.approve(
                user
        );

        /*
         * approvedCount에는 방금 승인한 사람이
         * 포함되지 않았으므로 1명을 더한다.
         */
        long approvedCountAfterApproval =
                approvedCount + 1;

        /*
         * 필요한 인원을 모두 승인하면
         * 모집 완료 상태로 자동 변경한다.
         */
        if (
                approvedCountAfterApproval
                        >= activity.getRequiredPeople()
        ) {
            activity.changeStatus(
                    ActivityStatus.READY
            );
        }

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

    /**
     * 봉사자가 제출한 활동기록을 기관 담당자가 조회한다.
     */
    @Transactional(readOnly = true)
    public InstitutionActivityRecordResponse
    getInstitutionActivityRecord(
            Long userId,
            Long activityId
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        findInstitutionActivity(
                institution.getId(),
                activityId
        );

        ActivityRecord record =
                findActivityRecord(activityId);

        List<ChecklistResponse> responses =
                checklistResponseRepository
                        .findByActivityRecordId(
                                record.getId()
                        );

        return InstitutionActivityRecordResponse.from(
                record,
                responses
        );
    }

    /**
     * 기관 담당자가 제출된 활동기록을 승인한다.
     *
     * 기록이 승인되면 활동도 완료 상태로 변경한다.
     */
    @Transactional
    public InstitutionActivityRecordResponse
    approveInstitutionActivityRecord(
            Long userId,
            Long activityId
    ) {
        User reviewer =
                findUser(userId);

        Institution institution =
                getInstitution(reviewer);

        CareActivity activity =
                findInstitutionActivity(
                        institution.getId(),
                        activityId
                );

        ActivityRecord record =
                findActivityRecord(activityId);

        validateSubmittedRecord(record);

        if (
                activity.getStatus()
                        != ActivityStatus.IN_PROGRESS
        ) {
            throw new CustomException(
                    ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT
            );
        }

        record.approveReview(reviewer);

        activity.changeStatus(
                ActivityStatus.COMPLETED
        );

        List<ChecklistResponse> responses =
                checklistResponseRepository
                        .findByActivityRecordId(
                                record.getId()
                        );

        return InstitutionActivityRecordResponse.from(
                record,
                responses
        );
    }

    /**
     * 기관 담당자가 제출된 활동기록에 보완을 요청한다.
     */
    @Transactional
    public InstitutionActivityRecordResponse
    requestInstitutionActivityRecordRevision(
            Long userId,
            Long activityId,
            InstitutionActivityRecordRevisionRequest request
    ) {
        User reviewer =
                findUser(userId);

        Institution institution =
                getInstitution(reviewer);

        findInstitutionActivity(
                institution.getId(),
                activityId
        );

        ActivityRecord record =
                findActivityRecord(activityId);

        validateSubmittedRecord(record);

        record.requestRevision(
                reviewer,
                request.reviewNote().trim()
        );

        List<ChecklistResponse> responses =
                checklistResponseRepository
                        .findByActivityRecordId(
                                record.getId()
                        );

        return InstitutionActivityRecordResponse.from(
                record,
                responses
        );
    }

    /**
     * 활동에 작성된 결과 기록을 조회한다.
     */
    private ActivityRecord findActivityRecord(
            Long activityId
    ) {
        return institutionActivityRepository
                .findActivityRecord(activityId)
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
                );
    }

    /**
     * 기관 검토 대기 상태인지 검사한다.
     */
    private void validateSubmittedRecord(
            ActivityRecord record
    ) {
        if (
                record.getReviewStatus()
                        != ActivityReviewStatus.SUBMITTED
        ) {
            throw new CustomException(
                    ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT
            );

        }
    }

    /**
     * 기관의 승인 대기 봉사 신청 현황을 조회한다.
     *
     * 사이드바에는 전체 대기 신청자 수를 표시하고,
     * 활동 관리 화면에는 활동별 대기 현황을 표시한다.
     */
    @Transactional(readOnly = true)
    public InstitutionPendingApplicationSummaryResponse
    getPendingApplicationSummary(
            Long userId
    ) {
        User user =
                findUser(userId);

        Institution institution =
                getInstitution(user);

        Long institutionId =
                institution.getId();

        /*
         * 모집 중인 활동에 들어온
         * 전체 승인 대기 신청자 수를 조회한다.
         */
        long totalPendingCount =
                institutionActivityRepository
                        .countInstitutionApplications(
                                institutionId,
                                ActivityStatus.RECRUITING,
                                ApplicationStatus.PENDING
                        );

        /*
         * 승인 대기 신청이 존재하는 활동을
         * 활동별로 집계한다.
         */
        List<
                InstitutionActivityRepository
                        .PendingApplicationActivityProjection
                >
                projections =
                institutionActivityRepository
                        .findPendingApplicationActivities(
                                institutionId,
                                ActivityStatus.RECRUITING,
                                ApplicationStatus.PENDING
                        );

        List<
                InstitutionPendingApplicationSummaryResponse
                        .PendingActivityResponse
                >
                activities =
                projections
                        .stream()
                        .map(
                                projection ->
                                        new InstitutionPendingApplicationSummaryResponse
                                                .PendingActivityResponse(
                                                projection.getActivityId(),
                                                projection.getRecipientId(),
                                                projection.getRecipientName(),
                                                projection.getScheduledAt(),
                                                projection.getPendingCount()
                                        )
                        )
                        .toList();

        return new InstitutionPendingApplicationSummaryResponse(
                totalPendingCount,
                activities
        );
    }
}