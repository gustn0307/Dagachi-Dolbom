package com.dagachi.backend.user.mypage.dto;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserStatus;

// USER-01, USER-02 공통 응답 DTO
public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String nickname,
        String phone,
        UserGender gender,
        UserStatus status
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getGender(),
                user.getStatus()
        );
    }
}