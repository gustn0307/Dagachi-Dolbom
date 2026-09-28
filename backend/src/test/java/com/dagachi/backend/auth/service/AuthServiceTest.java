package com.dagachi.backend.auth.service;

import com.dagachi.backend.auth.dto.SignupRequest;
import com.dagachi.backend.auth.dto.SignupResponse;
import com.dagachi.backend.auth.dto.LoginRequest;
import com.dagachi.backend.auth.dto.LoginResponse;
import com.dagachi.backend.auth.dto.MeResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.security.jwt.JwtTokenProvider;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * AuthService 회원가입 단위 테스트.
 * <p>
 * REQ-AUTH-01/02:
 * - 신규 이메일은 회원가입할 수 있다.
 * - 이미 존재하는 이메일은 탈퇴 여부와 관계없이 재가입할 수 없다.
 * <p>
 * 주의:
 * 이 클래스는 UserRepository를 Mock으로 사용하는 Service 단위 테스트이므로,
 * 실제 Soft Delete된 DB 행까지 existsByEmail()이 조회하는지는
 * 별도의 PostgreSQL Repository 통합 테스트에서 검증한다.
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider
        );
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "user@test.com",
                "Password123!",
                "홍길동",
                "길동이",
                "010-1234-5678",
                UserGender.MALE
        );
    }

    @Test
    @DisplayName("REQ-AUTH-01/02 - 신규 이메일이면 회원가입에 성공한다")
    void signup_신규_이메일이면_회원가입에_성공한다() {
        // given
        SignupRequest request = signupRequest();

        given(userRepository.existsByEmail(request.email()))
                .willReturn(false);

        given(passwordEncoder.encode(request.password()))
                .willReturn("encoded-password");

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 1L);
                    return user;
                });

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@test.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.nickname()).isEqualTo("길동이");
        assertThat(response.role()).isEqualTo(UserRole.USER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo(request.email());
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getDeleted()).isFalse();

        verify(userRepository).existsByEmail(request.email());
        verify(passwordEncoder).encode(request.password());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("REQ-AUTH-01/02 - ACTIVE 계정과 동일한 이메일이면 회원가입을 거부한다")
    void signup_ACTIVE_계정과_동일한_이메일이면_회원가입을_거부한다() {
        // given
        SignupRequest request = signupRequest();

        given(userRepository.existsByEmail(request.email()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException = (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                });

        verify(userRepository).existsByEmail(request.email());

        // 이메일 중복이 확인되면 비밀번호 암호화나 DB 저장까지 진행하면 안 된다.
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("REQ-AUTH-01/02 - WITHDRAWN Soft Delete 계정의 이메일도 재가입을 거부한다")
    void signup_WITHDRAWN_SoftDelete_계정의_이메일도_재가입을_거부한다() {
        // given
        SignupRequest request = signupRequest();

        /*
         * AuthService는 deleted 상태를 직접 조회하지 않고,
         * deleted 여부와 관계없이 전체 이메일을 검사하는
         * existsByEmail() 결과를 기준으로 가입을 차단한다.
         *
         * 실제 Soft Delete된 DB 행도 existsByEmail()에서 검색되는지는
         * 별도의 Repository 통합 테스트에서 검증한다.
         */
        given(userRepository.existsByEmail(request.email()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException = (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                });

        verify(userRepository).existsByEmail(request.email());

        // DB UNIQUE 오류까지 내려가기 전에 Service 단계에서 차단되어야 한다.
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("REQ-AUTH-03 - ACTIVE 사용자는 올바른 비밀번호로 로그인하면 JWT를 발급받는다")
    void login_ACTIVE_사용자는_올바른_비밀번호로_로그인에_성공한다() {
        // given
        LoginRequest request = new LoginRequest(
                "login@test.com",
                "Password123!"
        );

        User user = User.create(
                "login@test.com",
                "encoded-password",
                "로그인 사용자",
                "로그인테스터",
                "01012345678",
                UserGender.MALE
        );

        ReflectionTestUtils.setField(user, "id", 10L);

        given(userRepository.findByEmailAndDeletedFalse(request.email()))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )).willReturn(true);

        given(jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getRole().name()
        )).willReturn("access-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("login@test.com");
        assertThat(response.name()).isEqualTo("로그인 사용자");
        assertThat(response.role()).isEqualTo(UserRole.USER);

        verify(userRepository)
                .findByEmailAndDeletedFalse(request.email());

        verify(passwordEncoder)
                .matches(request.password(), "encoded-password");

        verify(jwtTokenProvider)
                .createAccessToken(10L, UserRole.USER.name());
    }


    @Test
    @DisplayName("REQ-AUTH-03 - 비밀번호가 일치하지 않으면 INVALID_CREDENTIALS로 로그인을 거부한다")
    void login_잘못된_비밀번호면_INVALID_CREDENTIALS() {
        // given
        LoginRequest request = new LoginRequest(
                "login@test.com",
                "WrongPassword!"
        );

        User user = User.create(
                "login@test.com",
                "encoded-password",
                "로그인 사용자",
                "로그인테스터",
                "01012345678",
                UserGender.MALE
        );

        given(userRepository.findByEmailAndDeletedFalse(request.email()))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                });

        verify(userRepository)
                .findByEmailAndDeletedFalse(request.email());

        verify(passwordEncoder)
                .matches(request.password(), "encoded-password");

        /*
         * 비밀번호 검증에 실패했으므로
         * JWT가 발급되어서는 안 됩니다.
         */
        verifyNoInteractions(jwtTokenProvider);
    }


    @Test
    @DisplayName("REQ-AUTH-03 - SUSPENDED 계정은 올바른 비밀번호여도 로그인을 거부한다")
    void login_SUSPENDED_계정은_ACCOUNT_SUSPENDED() {
        // given
        LoginRequest request = new LoginRequest(
                "suspended@test.com",
                "Password123!"
        );

        /*
         * User.create()는 ACTIVE USER만 생성하므로,
         * 이 테스트에서는 로그인 Service의 상태 분기만 검증하기 위해
         * User Entity를 Mock으로 구성합니다.
         */
        User suspendedUser = mock(User.class);

        given(suspendedUser.getPassword())
                .willReturn("encoded-password");

        given(suspendedUser.getStatus())
                .willReturn(UserStatus.SUSPENDED);

        given(userRepository.findByEmailAndDeletedFalse(request.email()))
                .willReturn(Optional.of(suspendedUser));

        given(passwordEncoder.matches(
                request.password(),
                suspendedUser.getPassword()
        )).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
                });

        verify(userRepository)
                .findByEmailAndDeletedFalse(request.email());

        verify(passwordEncoder)
                .matches(request.password(), "encoded-password");

        /*
         * 계정 상태가 SUSPENDED이면
         * 비밀번호가 맞더라도 JWT를 발급하면 안 됩니다.
         */
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("REQ-AUTH-03 - 탈퇴되어 조회 대상에서 제외된 계정은 로그인을 거부한다")
    void login_WITHDRAWN_계정은_로그인을_거부한다() {
        // given
        LoginRequest request = new LoginRequest(
                "withdrawn@test.com",
                "Password123!"
        );

        /*
         * 실제 회원탈퇴 사용자는 deleted=true가 되므로
         * findByEmailAndDeletedFalse()의 조회 대상에서 제외됩니다.
         *
         * 따라서 AuthService는 이메일/비밀번호가 존재하는지 외부에 구분해서
         * 노출하지 않고 INVALID_CREDENTIALS로 로그인을 차단합니다.
         */
        given(userRepository.findByEmailAndDeletedFalse(request.email()))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                });

        verify(userRepository)
                .findByEmailAndDeletedFalse(request.email());

        /*
         * 사용자 조회 단계에서 이미 차단되므로
         * 비밀번호 비교와 JWT 발급은 수행하면 안 됩니다.
         */
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("REQ-AUTH-05 - 존재하는 사용자 ID로 현재 사용자 정보를 조회한다")
    void getMe_존재하는_사용자면_현재사용자정보를_반환한다() {
        // given
        User user = User.create(
                "me@test.com",
                "encoded-password",
                "현재 사용자",
                "현재사용자닉네임",
                "01098765432",
                UserGender.FEMALE
        );

        ReflectionTestUtils.setField(user, "id", 20L);

        given(userRepository.findByIdAndDeletedFalse(20L))
                .willReturn(Optional.of(user));

        // when
        MeResponse response = authService.getMe(20L);

        // then
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.email()).isEqualTo("me@test.com");
        assertThat(response.name()).isEqualTo("현재 사용자");
        assertThat(response.nickname()).isEqualTo("현재사용자닉네임");
        assertThat(response.phone()).isEqualTo("01098765432");
        assertThat(response.gender()).isEqualTo(UserGender.FEMALE);
        assertThat(response.role()).isEqualTo(UserRole.USER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);

        /*
         * 일반 USER는 institution에 소속되지 않으므로 null이어야 합니다.
         * INSTITUTION 사용자의 institutionId 변환 자체는
         * MeResponseTest에서 별도로 검증하고 있습니다.
         */
        assertThat(response.institutionId()).isNull();

        verify(userRepository)
                .findByIdAndDeletedFalse(20L);
    }

    @Test
    @DisplayName("REQ-AUTH-05 - 존재하지 않는 사용자 ID이면 USER_NOT_FOUND를 반환한다")
    void getMe_존재하지않는_사용자면_USER_NOT_FOUND() {
        // given
        Long userId = 999L;

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.getMe(userId))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND);
                });

        verify(userRepository)
                .findByIdAndDeletedFalse(userId);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
    }
}