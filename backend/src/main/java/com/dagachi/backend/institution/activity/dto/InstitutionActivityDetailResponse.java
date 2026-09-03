package com.dagachi.backend.institution.activity.dto;

import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.GenderCondition;

import java.time.LocalDateTime;

/**
 * ACT-05 기관 활동 상세 조회 응답.
 */
public record InstitutionActivityDetailResponse(

        // 활동 번호
        Long activityId,

        // 돌봄 대상자 번호
        Long recipientId,

        // 돌봄 대상자 이름
        String recipientName,

        // 돌봄 대상자 전화번호
        String recipientPhone,

        // 돌봄 대상자 주소
        String recipientAddress,

        // 돌봄 대상자 상세 주소
        String recipientDetailAddress,

        // 활동 예정 일시
        LocalDateTime scheduledAt,

        // 필요한 봉사자 수
        Integer requiredPeople,

        // 승인된 봉사자 수
        long approvedCount,

        // 승인 대기 중인 봉사자 수
        long pendingCount,

        // 참여 성별 조건
        GenderCondition genderCondition,

        // 활동 상태
        ActivityStatus status,

        // 처음 활동을 등록한 담당자 번호
        Long createdById,

        // 처음 활동을 등록한 담당자 이름
        String createdByName,

        // 활동 등록일
        LocalDateTime createdAt,

        // 활동 결과 기록 존재 여부
        boolean hasRecord,

        // 활동 결과 기록 번호
        Long recordId,

        // 활동 결과 검토 상태
        ActivityReviewStatus reviewStatus
) {

    /**
     * Entity를 상세 응답 DTO로 변환한다.
     *
     * 결과 기록이 없으면:
     * - hasRecord: false
     * - recordId: null
     * - reviewStatus: null
     */
    public static InstitutionActivityDetailResponse from(
            CareActivity activity,
            long approvedCount,
            long pendingCount,
            ActivityRecord record
    ) {
        boolean hasRecord = record != null;

        return new InstitutionActivityDetailResponse(
                activity.getId(),
                activity.getRecipient().getId(),
                activity.getRecipient().getName(),
                activity.getRecipient().getPhone(),
                activity.getRecipient().getAddress(),
                activity.getRecipient().getDetailAddress(),
                activity.getScheduledAt(),
                activity.getRequiredPeople(),
                approvedCount,
                pendingCount,
                activity.getGenderCondition(),
                activity.getStatus(),
                activity.getCreatedBy().getId(),
                activity.getCreatedBy().getName(),
                activity.getCreatedAt(),
                hasRecord,
                hasRecord ? record.getId() : null,
                hasRecord ? record.getReviewStatus() : null
        );
    }
}