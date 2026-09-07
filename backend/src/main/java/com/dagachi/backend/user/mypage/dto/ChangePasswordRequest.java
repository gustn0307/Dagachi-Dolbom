package com.dagachi.backend.user.mypage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// [팀 공유 필요 - API_SPEC 신규 항목] 비밀번호 변경
public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해 주세요.")
        String newPassword
) {
}