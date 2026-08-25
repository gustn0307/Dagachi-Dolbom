package com.dagachi.backend.institution.recipient.dto;

import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.UserGender;

import java.time.LocalDateTime;
/**
 * 기관 담당자가 돌봄 대상자 상세 화면에서 사용하는 응답 DTO.
 *
 * CARE-02
 * GET /api/institution/care-recipients/{recipientId}
 *
 * CareRecipient Entity를 API 응답으로 직접 반환하지 않고,
 * 상세 화면에 필요한 정보만 전달하기 위해 사용한다.
 *
 * 기관 전용 DTO이므로 일반 USER 활동 조회 응답과 공유하지 않는다.
 */

public record CareRecipientDetailResponse(
        Long recipientId,// 돌봄 대상자 고유 ID
        String name,// 돌봄 대상자 이름
        UserGender gender,// 돌봄 대상자 성별: MALE 또는 FEMALE
        Integer birthYear,// 출생연도. 정확한 생년월일은 저장하지 않으며 값이 없을 수 있다.
        String address,// 기본 주소
        CareRecipientStatus status,// 대상자 관리 상태: ACTIVE 또는 INACTIVE
        ConsentStatus consentStatus,// 서비스 참여 동의 상태: PENDING, AGREED 또는 WITHDRAWN

        // 가장 최근에 정상적으로 안부확인을 완료한 시각
        // ActivityRecord가 APPROVED이고 VisitResult가 MET인 경우에만 갱신된다.
        // 아직 정상 안부확인 기록이 없다면 null일 수 있다.
        LocalDateTime lastCheckedAt,

        // 대상자 연락처
        // 개인정보이므로 목록에서는 제외하고 기관 상세 화면에서만 반환한다.
        String phone,

        // 대상자의 상세주소
        // 개인정보이므로 목록에서는 제외하고 기관 상세 화면에서만 반환한다.
        String detailAddress,

        // 대상자가 서비스 참여에 동의한 시각
        // consentStatus가 AGREED가 아니거나 아직 동의하지 않았다면 null일 수 있다.
        LocalDateTime consentAt,

        // 대상자가 기존 서비스 참여 동의를 철회한 시각
        // 동의를 철회한 적이 없다면 null일 수 있다.
        LocalDateTime consentWithdrawnAt,

        // 이 대상자와 연결된 제보 수
        // CareRecipient와 연결된 Report 개수를 Repository에서 조회한다.
        long reportCount,

        // 이 대상자와 연결된 활동 수
        // 어떤 활동을 횟수에 포함할지는 팀 정책을 확인해야 한다.
        // 예: 전체 CareActivity 수 또는 완료된 활동 수
        long activityCount
) {


    /**
     * CareRecipient Entity와 제보·활동 건수를
     * CareRecipientDetailResponse DTO로 변환한다.
     *
     * reportCount와 activityCount는 CareRecipient Entity 필드가 아니므로
     * Service가 각각 Repository에서 조회한 뒤 전달한다.
     *
     * @param recipient 조회된 돌봄 대상자 Entity
     * @param reportCount 해당 대상자와 연결된 제보 수
     * @param activityCount 해당 대상자와 연결된 활동 수
     * @return 기관 대상자 상세 응답 DTO
     */
    public static CareRecipientDetailResponse of(
            CareRecipient recipient,
            long reportCount,
            long activityCount
    ) {
        return new CareRecipientDetailResponse(
                // Entity의 PK를 API의 recipientId로 반환
                recipient.getId(),

                // 기본 인적 정보
                recipient.getName(),
                recipient.getGender(),
                recipient.getBirthYear(),

                // 기본 주소 및 관리 상태
                recipient.getAddress(),
                recipient.getStatus(),
                recipient.getConsentStatus(),

                // 최근 정상 안부확인 시각
                recipient.getLastCheckedAt(),

                // 상세 화면에서만 사용하는 개인정보
                recipient.getPhone(),
                recipient.getDetailAddress(),

                // 동의 및 동의 철회 시각
                recipient.getConsentAt(),
                recipient.getConsentWithdrawnAt(),

                // Service에서 별도로 조회한 연관 데이터 건수
                reportCount,
                activityCount
        );
    }
}