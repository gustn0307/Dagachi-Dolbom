package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.dto.ChangePasswordRequest;
import com.dagachi.backend.user.mypage.dto.UpdateProfileRequest;
import com.dagachi.backend.user.mypage.dto.UserProfileResponse;
import com.dagachi.backend.user.mypage.dto.WithdrawRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UserProfileService(USER-01~03, 비밀번호 변경) 비즈니스 규칙 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityApplicationRepository activityApplicationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserProfileService userProfileService;

    private static final Long USER_ID = 100L;

    private User buildUser(String encodedPassword) {
        User user = User.create(
                "user@test.com", encodedPassword, "홍길동", "길동이", "010-1234-5678", UserGender.MALE
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    // ---------------------------------------------------------------
    // USER-01 내 프로필 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("USER-01 내 프로필 조회 - 활성 계정이면 프로필을 반환한다")
    void getMyProfile_활성계정이면_프로필을_반환한다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        UserProfileResponse response = userProfileService.getMyProfile(USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("USER-01 내 프로필 조회 - 탈퇴/삭제된 계정이면 USER_NOT_FOUND")
    void getMyProfile_탈퇴계정이면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMyProfile(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ---------------------------------------------------------------
    // USER-02 내 프로필 수정
    // ---------------------------------------------------------------

    @Test
    @DisplayName("USER-02 내 프로필 수정 - 닉네임/전화번호를 변경한다")
    void updateMyProfile_닉네임과_전화번호를_변경한다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        UserProfileResponse response = userProfileService.updateMyProfile(
                USER_ID, new UpdateProfileRequest("새닉네임", "010-9999-8888")
        );

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.phone()).isEqualTo("010-9999-8888");
    }

    @Test
    @DisplayName("USER-02 내 프로필 수정 - null로 보낸 필드는 기존 값을 유지한다")
    void updateMyProfile_null필드는_기존값을_유지한다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        UserProfileResponse response = userProfileService.updateMyProfile(
                USER_ID, new UpdateProfileRequest(null, "010-9999-8888")
        );

        assertThat(response.nickname()).isEqualTo("길동이"); // 기존 닉네임 유지
        assertThat(response.phone()).isEqualTo("010-9999-8888");
    }

    // ---------------------------------------------------------------
    // 비밀번호 변경
    // ---------------------------------------------------------------

    @Test
    @DisplayName("비밀번호 변경 - 현재 비밀번호가 틀리면 PASSWORD_MISMATCH")
    void changePassword_현재비밀번호가_틀리면_예외를_던진다() {
        User user = buildUser("encoded-old-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-pw", "encoded-old-pw")).willReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("wrong-pw", "new-password-1234");

        assertThatThrownBy(() -> userProfileService.changePassword(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("비밀번호 변경 - 새 비밀번호가 현재 비밀번호와 같으면 PASSWORD_SAME_AS_CURRENT")
    void changePassword_새비밀번호가_기존과_같으면_예외를_던진다() {
        User user = buildUser("encoded-old-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("current-pw", "encoded-old-pw")).willReturn(true);
        given(passwordEncoder.matches("current-pw-new", "encoded-old-pw")).willReturn(true); // 새 비번도 기존과 일치

        ChangePasswordRequest request = new ChangePasswordRequest("current-pw", "current-pw-new");

        assertThatThrownBy(() -> userProfileService.changePassword(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_SAME_AS_CURRENT);
    }

    @Test
    @DisplayName("비밀번호 변경 - 정상 요청이면 새 비밀번호를 암호화하여 저장한다")
    void changePassword_정상요청이면_암호화하여_저장한다() {
        User user = buildUser("encoded-old-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("current-pw", "encoded-old-pw")).willReturn(true);
        given(passwordEncoder.matches("new-pw", "encoded-old-pw")).willReturn(false);
        given(passwordEncoder.encode("new-pw")).willReturn("encoded-new-pw");

        userProfileService.changePassword(USER_ID, new ChangePasswordRequest("current-pw", "new-pw"));

        assertThat(user.getPassword()).isEqualTo("encoded-new-pw");
    }

    // ---------------------------------------------------------------
    // USER-03 회원 탈퇴
    // ---------------------------------------------------------------

    @Test
    @DisplayName("USER-03 회원 탈퇴 - 비밀번호가 틀리면 PASSWORD_MISMATCH")
    void withdraw_비밀번호가_틀리면_예외를_던진다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-pw", "encoded-pw")).willReturn(false);

        assertThatThrownBy(() -> userProfileService.withdraw(USER_ID, new WithdrawRequest("wrong-pw")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("USER-03 회원 탈퇴 - 진행 중인 신청/활동이 있으면 WITHDRAWAL_BLOCKED")
    void withdraw_진행중인_신청이_있으면_예외를_던진다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-pw", "encoded-pw")).willReturn(true);
        given(
                activityApplicationRepository
                        .existsBlockingWithdrawalParticipation(USER_ID)
        ).willReturn(true);

        assertThatThrownBy(() -> userProfileService.withdraw(USER_ID, new WithdrawRequest("correct-pw")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED);

        assertThat(user.getStatus()).isNotEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("USER-03 회원 탈퇴 - 진행 중인 신청이 없으면 Soft Delete + WITHDRAWN 처리한다")
    void withdraw_정상요청이면_탈퇴처리된다() {
        User user = buildUser("encoded-pw");
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-pw", "encoded-pw")).willReturn(true);
        given(
                activityApplicationRepository
                        .existsBlockingWithdrawalParticipation(USER_ID)
        ).willReturn(false);

        userProfileService.withdraw(USER_ID, new WithdrawRequest("correct-pw"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }
}