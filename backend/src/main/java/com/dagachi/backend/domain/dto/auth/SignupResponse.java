package com.dagachi.backend.domain.dto.auth;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;

public record SignupResponse(
        Long id,
        String email,
        String name,
        String nickname,
        UserRole role,
        UserStatus status
) {

    //    Entity → Response DTO 변환
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getRole(),
                user.getStatus()
        );
    }
}