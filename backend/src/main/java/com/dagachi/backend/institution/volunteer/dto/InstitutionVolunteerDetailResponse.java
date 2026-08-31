package com.dagachi.backend.institution.volunteer.dto;

import com.dagachi.backend.domain.enums.UserGender;

import java.time.LocalDateTime;

/**
 * VOL-06 기관 봉사자 기본 상세 응답.
 *
 * 해당 기관에서의 참여 횟수와 최근 활동일을 포함한다.
 */
public record InstitutionVolunteerDetailResponse(

        // 봉사자의 사용자 ID
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

        // 해당 기관에서 가장 최근에 활동을 완료한 시각
        LocalDateTime lastParticipatedAt
) {
}