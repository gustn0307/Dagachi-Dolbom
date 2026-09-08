package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.dto.ChangePasswordRequest;
import com.dagachi.backend.user.mypage.dto.UpdateProfileRequest;
import com.dagachi.backend.user.mypage.dto.UserProfileResponse;
import com.dagachi.backend.user.mypage.dto.WithdrawRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final ActivityApplicationRepository activityApplicationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(
            UserRepository userRepository,
            ActivityApplicationRepository activityApplicationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.activityApplicationRepository = activityApplicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // USER-01
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findActiveUser(userId);

        return UserProfileResponse.from(user);
    }

    // USER-02 (비밀번호 확인 없음 - 낮은 민감도 필드만 다룸)
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        user.updateProfile(request.nickname(), request.phone());

        return UserProfileResponse.from(user);
    }

    // 비밀번호 변경
    // [팀 공유 필요 - API_SPEC 신규] 현재 비밀번호 확인 후 새 비밀번호로 교체
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 새 비밀번호가 기존 비밀번호와 동일한지 확인
        // matches()는 (평문, 암호문)을 비교하므로 새 비밀번호를 그대로 넣어 비교한다.
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedNewPassword);
    }

    // USER-03
    // [팀 미확정 정책 임시 적용] 순서:
    // 1) 비밀번호 확인
    // 2) 진행 중(PENDING/APPROVED) 신청·활동 존재 여부 확인
    // 3) 통과 시 Soft Delete + WITHDRAWN 처리
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        boolean hasActiveParticipation = activityApplicationRepository
                .existsByUser_IdAndStatusIn(
                        userId,
                        List.of(ApplicationStatus.PENDING, ApplicationStatus.APPROVED)
                );

        if (hasActiveParticipation) {
            throw new CustomException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        user.withdraw();
    }

    // 탈퇴·삭제된 계정은 대상에서 제외하고 조회
    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}