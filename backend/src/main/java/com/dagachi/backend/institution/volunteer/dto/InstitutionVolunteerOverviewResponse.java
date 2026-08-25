package com.dagachi.backend.institution.volunteer.dto;

/**
 * VOL-05 기관 봉사자 현황 요약 응답.
 *
 * 봉사자 관리 화면 상단의 통계 카드에서 사용한다.
 */
public record InstitutionVolunteerOverviewResponse(

        // 해당 기관의 활동에 한 번 이상 참여 완료한 전체 봉사자 수
        Long totalVolunteerCount,

        // 현재 진행 중인 활동에 참여하고 있는 봉사자 수
        Long activeVolunteerCount,

        // 앞으로 진행될 준비 완료 활동에 승인된 봉사자 수
        Long scheduledVolunteerCount
) {
}