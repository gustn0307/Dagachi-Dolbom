package com.dagachi.backend.user.record.dto;

import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;

import java.time.LocalDateTime;

public record ActivityRecordResponse(
        Long activityRecordId,
        Long activityId,
        ActivityReviewStatus reviewStatus,
        Integer checklistVersion,
        LocalDateTime startedAt
) {
    public static ActivityRecordResponse from(ActivityRecord record) {
        return new ActivityRecordResponse(
                record.getId(),
                record.getActivity().getId(),
                record.getReviewStatus(),
                record.getChecklistVersion(),
                record.getStartedAt()
        );
    }
}