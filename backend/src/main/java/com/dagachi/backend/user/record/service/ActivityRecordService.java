package com.dagachi.backend.user.record.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.user.record.dto.ActivityRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일반 USER의 활동 시작(RECORD-01)을 담당한다.
 */
@Service
public class ActivityRecordService {

    private final CareActivityRepository careActivityRepository;
    private final ActivityApplicationRepository activityApplicationRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final ChecklistItemRepository checklistItemRepository;

    public ActivityRecordService(
            CareActivityRepository careActivityRepository,
            ActivityApplicationRepository activityApplicationRepository,
            ActivityRecordRepository activityRecordRepository,
            ChecklistItemRepository checklistItemRepository
    ) {
        this.careActivityRepository = careActivityRepository;
        this.activityApplicationRepository = activityApplicationRepository;
        this.activityRecordRepository = activityRecordRepository;
        this.checklistItemRepository = checklistItemRepository;
    }

    @Transactional
    public ActivityRecordResponse startActivity(Long activityId, Long userId) {

        // 동시성 방지: 신청 승인/취소와 동일하게 CareActivity를 PESSIMISTIC_WRITE로 잠근다.
        CareActivity activity = careActivityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (activity.getStatus() != ActivityStatus.READY) {
            throw new CustomException(ErrorCode.ACTIVITY_NOT_READY);
        }

        ActivityApplication myApplication = activityApplicationRepository
                .findByActivity_IdAndUser_Id(activityId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));

        if (myApplication.getStatus() != ApplicationStatus.APPROVED) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (activityRecordRepository.findByActivity_Id(activityId).isPresent()) {
            throw new CustomException(ErrorCode.ACTIVITY_ALREADY_STARTED);
        }

        // 정원 재검증 (락을 잡은 상태이므로 최신 값 기준)
        long approvedCount = activityApplicationRepository
                .countApprovedMap(List.of(activityId))
                .getOrDefault(activityId, 0L);

        if (approvedCount < activity.getRequiredPeople()) {
            throw new CustomException(ErrorCode.ACTIVITY_NOT_READY);
        }

        // 성별 조건 재검증
        if (activity.getGenderCondition() == GenderCondition.SAME_GENDER_ONE) {
            List<UserGender> genders = activityApplicationRepository.findApprovedUserGenders(activityId);
            boolean allSameGender = genders.stream().distinct().count() <= 1;
            if (!allSameGender) {
                throw new CustomException(ErrorCode.ACTIVITY_GENDER_CONDITION_NOT_MET);
            }
        }

        Integer checklistVersion = checklistItemRepository.findCurrentActiveVersion()
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        ActivityRecord record = ActivityRecord.createDraft(activity, checklistVersion, LocalDateTime.now());
        ActivityRecord saved = activityRecordRepository.save(record);

        activity.changeStatus(ActivityStatus.IN_PROGRESS);

        return ActivityRecordResponse.from(saved);
    }
}