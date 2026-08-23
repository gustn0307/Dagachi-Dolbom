package com.dagachi.backend.auth.service;

import com.dagachi.backend.auth.dto.*;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.security.jwt.JwtTokenProvider;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 회원가입
    // 회원가입 저장 작업을 하나의 트랜잭션 경계로 관리
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 이메일 중복 확인
        if (userRepository.existsByEmailAndDeletedFalse(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 유저 엔티티 생성
        User user = User.create(
                request.email(),
                encodedPassword,
                request.name(),
                request.nickname(),
                request.phone(),
                request.gender()
        );

        // DB 저장
        User savedUser = userRepository.save(user);

        // Entity -> DTO 변환해서 리턴
        return SignupResponse.from(savedUser);
    }

    //    LoginRequest
//    ↓
//    email로 User 조회
//    ↓
//    없음 ───────────→ INVALID_CREDENTIALS
//    ↓
//    BCrypt matches()
//    ↓
//    불일치 ─────────→ INVALID_CREDENTIALS
//    ↓
//    UserStatus 확인
//    ├─ SUSPENDED → 403
//            ├─ WITHDRAWN → 403
//            └─ ACTIVE
//         ↓
//    JwtTokenProvider
//         ↓
//    Access Token 생성
//         ↓
//    LoginResponse
    // 로그인
    @Transactional(readOnly = true) // 조회만
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() ->
                        new CustomException(ErrorCode.INVALID_CREDENTIALS)
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getRole().name()
        );

        return LoginResponse.of(accessToken, user);
    }

    public MeResponse getMe(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return MeResponse.from(user);
    }
}
