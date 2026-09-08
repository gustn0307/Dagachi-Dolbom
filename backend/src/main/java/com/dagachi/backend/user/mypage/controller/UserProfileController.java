package com.dagachi.backend.user.mypage.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.mypage.dto.ChangePasswordRequest;
import com.dagachi.backend.user.mypage.dto.UpdateProfileRequest;
import com.dagachi.backend.user.mypage.dto.UserProfileResponse;
import com.dagachi.backend.user.mypage.dto.WithdrawRequest;
import com.dagachi.backend.user.mypage.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // USER-01
    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId
    ) {
        UserProfileResponse response = userProfileService.getMyProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success("내 프로필을 조회했습니다.", response)
        );
    }

    // USER-02
    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserProfileResponse response = userProfileService.updateMyProfile(userId, request);

        return ResponseEntity.ok(
                ApiResponse.success("내 프로필을 수정했습니다.", response)
        );
    }

    // [팀 공유 필요 - API_SPEC 신규] 비밀번호 변경
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userProfileService.changePassword(userId, request);

        return ResponseEntity.ok(
                ApiResponse.success("비밀번호가 변경되었습니다.", null)
        );
    }

    // USER-03
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawRequest request
    ) {
        userProfileService.withdraw(userId, request);

        return ResponseEntity.ok(
                ApiResponse.success("회원 탈퇴가 완료되었습니다.", null)
        );
    }
}