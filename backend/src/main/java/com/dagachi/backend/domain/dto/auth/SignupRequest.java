package com.dagachi.backend.domain.dto.auth;

import com.dagachi.backend.domain.enums.UserGender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// DTO는 Entity처럼 상태가 계속 바뀌는 객체가 아니라 요청 데이터를 전달하는 용도
// 요청/응답 DTO에는 record를 활용하기 좋다.
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @Size(max = 100, message = "닉네임은 100자 이하여야 합니다.")
        String nickname,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Size(max = 30, message = "전화번호는 30자 이하여야 합니다.")
        String phone,

        @NotNull(message = "성별은 필수입니다.")
        UserGender gender
) {
}