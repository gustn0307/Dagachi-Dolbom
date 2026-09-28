package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.dto.ChangePasswordRequest;
import com.dagachi.backend.user.mypage.dto.UpdateProfileRequest;
import com.dagachi.backend.user.mypage.dto.UserProfileResponse;
import com.dagachi.backend.user.mypage.dto.WithdrawRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [수정] REQ-AUTH-06: "USER만 본인의 최소 회원정보와 USER 전용 API에 접근한다.
 * INSTITUTION·ADMIN은 /api/users/me/** 및 USER 활동·신청·기록 API에 접근하지
 * 못한다."는 확정 요구사항이 지금까지 구현되어 있지 않았다.
 *
 * SecurityConfig(강현수 담당)가 아직 /api/users/me/**에 USER Role 매처를
 * 걸지 않으므로, 우선 이 Service 레벨에서 Role을 재검증해 INSTITUTION/ADMIN
 * 계정의 접근을 차단한다. (근본적으로는 SecurityConfig 쪽 매처도 필요하지만
 * 그건 담당자 영역이라 이번 수정에 포함하지 않았다.)
 *
 * [수정 2 - 버그] withdraw()가 REQ-AUTH-08 정책을 정확히 구현한
 * existsBlockingWithdrawalParticipation() 대신, Repository 주석에
 * "[팀 미확정 정책 임시 적용]"이라고 명시된 구버전 메서드
 * existsByUser_IdAndStatusIn(PENDING, APPROVED)을 그대로 쓰고 있었다.
 *
 * 구버전 메서드는 연결된 CareActivity 상태를 전혀 보지 않아서,
 * APPROVED 신청의 활동이 이미 COMPLETED/CANCELED로 끝났어도
 * 무조건 탈퇴를 막는 버그가 있었다. REQ-AUTH-08이 요구하는 실제 정책
 * (PENDING이거나, APPROVED+RECRUITING/READY/IN_PROGRESS일 때만 차단)을
 * 정확히 구현한 existsBlockingWithdrawalParticipation()으로 교체했다.
 */
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
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedNewPassword);
    }

    // USER-03
    // [동시성 보완] withdraw / applyDirect / applyAuto가 같은 User row에
    // PESSIMISTIC_WRITE 락을 사용해, 탈퇴와 활동 신청이 동시에 진행되는
    // TOCTOU 상황을 방지한다.
    // 순서:
    // 1) User row 락 획득 (findByIdAndDeletedFalseForUpdate)
    // 2) Role 검증 (REQ-AUTH-06: INSTITUTION/ADMIN 탈퇴 차단)
    // 3) 비밀번호 확인
    // 4) [수정] REQ-AUTH-08 실제 탈퇴 차단 정책 확인
    //    (PENDING이거나, APPROVED + RECRUITING/READY/IN_PROGRESS인 경우만 차단.
    //    APPROVED + COMPLETED/CANCELED, REJECTED, CANCELED 이력은 허용)
    // 5) 통과 시 Soft Delete + WITHDRAWN 처리
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = userRepository.findByIdAndDeletedFalseForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // REQ-AUTH-06: INSTITUTION/ADMIN 계정은 USER 전용 API를 사용할 수 없다.
        if (user.getRole() != UserRole.USER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // [수정] REQ-AUTH-08: 연결된 CareActivity 상태까지 정확히 반영하는
        // 쿼리로 교체했다. (기존 existsByUser_IdAndStatusIn은 CareActivity 상태를
        // 보지 않아 COMPLETED/CANCELED된 활동의 APPROVED 이력도 탈퇴를 막는 버그가 있었다.)
        boolean hasBlockingParticipation = activityApplicationRepository
                .existsBlockingWithdrawalParticipation(userId);

        if (hasBlockingParticipation) {
            throw new CustomException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        user.withdraw();
    }

    /**
     * 삭제되지 않은 계정을 조회하되, REQ-AUTH-06에 따라
     * USER Role이 아니면 접근을 차단한다. (락 없는 단순 조회용)
     *
     * getMyProfile / updateMyProfile / changePassword가 공통으로 사용하므로
     * 이 메서드 하나만 고쳐도 세 API 모두 보호된다.
     */
    private User findActiveUser(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // REQ-AUTH-06: INSTITUTION/ADMIN 계정은 USER 전용 API를 사용할 수 없다.
        if (user.getRole() != UserRole.USER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return user;
    }
}