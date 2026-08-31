package com.dagachi.backend.institution.volunteer.dto;

import com.dagachi.backend.domain.enums.UserGender;

import java.time.LocalDateTime;

/**
 * VOL-01 기관 봉사자 목록의 한 행을 반환하는 응답 DTO.
 *
 * 해당 기관에서 승인 완료된 활동에 참여한 사용자만 조회한다.
 */
public record InstitutionVolunteerSummaryResponse(

        // 봉사자 사용자 ID
        Long userId,

        // 봉사자 이름
        String name,

        // 봉사자 닉네임
        String nickname,

        // 봉사자 전화번호
        String phone,

        // 봉사자 성별
        UserGender gender,

        // 해당 기관에서 참여 완료한 활동 수
        Long participationCount,

        // 해당 기관에서 가장 최근에 활동한 일시
        LocalDateTime lastParticipatedAt
) {
}