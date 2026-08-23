package com.dagachi.backend.auth.dto;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email,
        String name,
        UserRole role
) {

    public static LoginResponse of(
            String accessToken,
            User user
    ) {
        return new LoginResponse(
                accessToken,
                "Bearer",
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
    }
}
