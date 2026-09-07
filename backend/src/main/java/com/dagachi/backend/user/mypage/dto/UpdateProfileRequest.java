package com.dagachi.backend.user.mypage.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "닉네임은 최대 100자까지 입력할 수 있습니다.")
        String nickname,

        @Size(max = 30, message = "전화번호는 최대 30자까지 입력할 수 있습니다.")
        String phone
) {
}