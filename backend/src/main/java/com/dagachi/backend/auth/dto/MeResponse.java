package com.dagachi.backend.auth.dto;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;

public record MeResponse(
        Long id,
        String email,
        String name,
        String nickname,
        String phone,
        UserGender gender,
        UserRole role,
        UserStatus status
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
                user.getStatus()
        );
    }
}
