package com.dagachi.backend.user.mypage.dto;

import jakarta.validation.constraints.NotBlank;

// USER-03: [팀 미확정 정책 임시 적용] 본인확인은 비밀번호 재입력으로 처리
public record WithdrawRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}