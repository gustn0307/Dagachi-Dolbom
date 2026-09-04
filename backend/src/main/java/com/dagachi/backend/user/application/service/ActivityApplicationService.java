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
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 일반 USER의 활동 신청(APP-01) / 내 신청 목록(APP-03) / 내 활동 목록(APP-04)
 * / 신청 취소(APP-05) 비즈니스 로직을 담당한다.
 */
@Service
public class ActivityApplicationService {

    private final ActivityApplicationRepository activityApplicationRepository;
    private final CareActivityRepository careActivityRepository;
    private final UserRepository userRepository;

    public ActivityApplicationService(
            ActivityApplicationRepository activityApplicationRepository,
            CareActivityRepository careActivityRepository,
            UserRepository userRepository
    ) {
        this.activityApplicationRepository = activityApplicationRepository;
        this.careActivityRepository = careActivityRepository;
        this.userRepository = userRepository;
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

        return PageResponse.from(page.map(ApplicationResponse::from));
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