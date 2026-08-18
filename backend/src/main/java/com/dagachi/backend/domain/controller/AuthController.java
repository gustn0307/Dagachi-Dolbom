package com.dagachi.backend.domain.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.domain.dto.auth.LoginRequest;
import com.dagachi.backend.domain.dto.auth.LoginResponse;
import com.dagachi.backend.domain.dto.auth.SignupRequest;
import com.dagachi.backend.domain.dto.auth.SignupResponse;
import com.dagachi.backend.domain.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 회원가입
    // @Valid: SignupRequest에 넣은 @NotBlank, @Email, @Size 검증이 실제로 동작
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "회원가입이 완료되었습니다.",
                        response
                ));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "로그인에 성공했습니다.",
                        response
                )
        );
    }
}