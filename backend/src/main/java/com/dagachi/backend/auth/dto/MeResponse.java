package com.dagachi.backend.auth.dto;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.enums.UserRole;

public record MeResponse(
        Long id,
        String email,
        String name,
        String nickname,
        String phone,
        UserGender gender,
        UserRole role,
        UserStatus status,
        Long institutionId   // 추가
) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getGender(),
                user.getRole(),
                user.getStatus(),
                user.getInstitution() != null
                        ? user.getInstitution().getId()
                        : null   // USER/ADMIN은 소속 기관이 없을 수 있음
        );
    }
}