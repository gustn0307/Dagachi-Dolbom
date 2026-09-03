package com.dagachi.backend.user.application.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일반 USER의 활동 직접 신청(APP-01) 비즈니스 로직을 담당한다.
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
     * 동일 activity/user 조합이 없으면 새로 생성(PENDING),
     * CANCELED 상태로 남아있으면 기존 행을 재사용(reactivate)한다.
     * 그 외 상태(PENDING/APPROVED/REJECTED)로 이미 존재하면 409.
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

    private CareActivity findActivity(Long activityId) {
        return careActivityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}