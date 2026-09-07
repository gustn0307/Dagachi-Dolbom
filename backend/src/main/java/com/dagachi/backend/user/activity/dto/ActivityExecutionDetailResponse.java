package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.domain.entity.CareActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACT-03 승인 참여자 전용 수행정보 DTO.
 *
 * [팀 확인 필요] 연락처 포함 여부·정확한 field matrix가 API 스펙상 미확정 상태.
 * 현재는 실제 방문에 필요한 최소 정보(이름·주소·좌표)만 포함하고 연락처는 제외했다.
 * 접근 종료 시점(활동 완료 후에도 계속 조회 가능한지)도 미확정이라
 * 현재는 APPROVED 상태 여부만 검증한다.
 *
 * activityRecordId: 활동이 아직 시작 전(READY)이면 null이다.
 * 이미 IN_PROGRESS라 다른 참여자가 만든 ActivityRecord가 있으면
 * 그 id를 내려줘서 이 화면에서 바로 체크리스트로 진입할 수 있게 한다.
 */
public record ActivityExecutionDetailResponse(
        Long activityId,
        Long activityRecordId,
        String recipientName,
        String address,
        String detailAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime scheduledAt,
        Integer requiredPeople,
        Long approvedCount,
        String status,
        String genderCondition
) {

    public static ActivityExecutionDetailResponse of(
            CareActivity activity,
            long approvedCount,
            Long activityRecordId
    ) {
        var recipient = activity.getRecipient();

        return new ActivityExecutionDetailResponse(
                activity.getId(),
                activityRecordId,
                recipient.getName(),
                recipient.getAddress(),
                recipient.getDetailAddress(),
                recipient.getLatitude(),
                recipient.getLongitude(),
                activity.getScheduledAt(),
                activity.getRequiredPeople(),
                approvedCount,
                activity.getStatus().name(),
                activity.getGenderCondition().name()
        );
    }
}