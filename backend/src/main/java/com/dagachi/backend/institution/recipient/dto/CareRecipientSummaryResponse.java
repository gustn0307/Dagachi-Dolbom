package com.dagachi.backend.institution.recipient.dto;

import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.UserGender;

import java.time.LocalDateTime;

/**
 * 기관 담당자가 돌봄 대상자 목록 화면에서 사용하는 요약 응답 DTO.
 *
 * CARE-01
 * GET /api/institution/care-recipients
 *
 * 여러 대상자를 한 번에 조회하는 목록 API이므로
 * 화면에 필요한 최소한의 요약 정보만 반환한다.
 *
 * 전화번호, 상세주소, 동의 일시 등 상세정보는 포함하지 않는다.
 * 해당 정보는 CARE-02 상세 조회 DTO에서만 반환한다.
 *
 * 기관 전용 DTO이므로 일반 USER 활동 조회 응답과 공유하지 않는다.
 */
public record CareRecipientSummaryResponse(

        // 돌봄 대상자 고유 ID
        Long recipientId,

        // 돌봄 대상자 이름
        String name,

        // 돌봄 대상자 성별: MALE 또는 FEMALE
        UserGender gender,

        // 돌봄 대상자의 출생연도
        // 출생연도 정보가 등록되지 않았다면 null일 수 있다.
        Integer birthYear,

        // 대상자의 기본 주소
        // 상세주소는 개인정보이므로 목록 응답에 포함하지 않는다.
        String address,

        // 대상자 관리 상태: ACTIVE 또는 INACTIVE
        CareRecipientStatus status,

        // 서비스 참여 동의 상태: PENDING, AGREED 또는 WITHDRAWN
        ConsentStatus consentStatus,

        // 가장 최근에 정상적으로 안부확인을 완료한 시각
        // ActivityRecord가 APPROVED이고 VisitResult가 MET인 경우에만 갱신된다.
        // 아직 정상 안부확인 기록이 없다면 null일 수 있다.
        LocalDateTime lastCheckedAt

) {

    /**
     * CareRecipient Entity를 목록용 요약 DTO로 변환한다.
     *
     * Entity를 API 응답으로 직접 반환하지 않고,
     * 목록 화면에 필요한 필드만 선택하여 반환한다.
     *
     * @param recipient 조회된 돌봄 대상자 Entity
     * @return 기관 대상자 목록용 요약 응답 DTO
     */
    public static CareRecipientSummaryResponse from(
            CareRecipient recipient
    ) {
        return new CareRecipientSummaryResponse(
                // Entity의 PK를 API의 recipientId로 반환
                recipient.getId(),

                // 대상자 기본정보
                recipient.getName(),
                recipient.getGender(),
                recipient.getBirthYear(),

                // 목록에서 사용하는 기본 주소
                recipient.getAddress(),

                // 관리 상태와 동의 상태
                recipient.getStatus(),
                recipient.getConsentStatus(),

                // 최근 정상 안부확인 시각
                recipient.getLastCheckedAt()
        );
    }
}