package com.dagachi.backend.user.application.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
import com.dagachi.backend.user.application.service.ActivityApplicationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 일반 USER의 활동 신청/조회(APP-01, APP-03) API를 제공한다.
 */
@RestController
public class ActivityApplicationController {

    private final ActivityApplicationService activityApplicationService;

    public ActivityApplicationController(ActivityApplicationService activityApplicationService) {
        this.activityApplicationService = activityApplicationService;
    }

    /**
     * APP-01 직접 신청.
     *
     * POST /api/activities/{activityId}/applications
     */
    @PostMapping("/api/activities/{activityId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ApplicationResponse response = activityApplicationService.applyDirect(activityId, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("활동 신청이 접수되었습니다.", response));
    }

    /**
     * APP-03 내 신청 목록 조회.
     *
     * GET /api/users/me/activity-applications
     */
    @GetMapping("/api/users/me/activity-applications")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) ApplicationType applicationType,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PageResponse<ApplicationResponse> response =
                activityApplicationService.getMyApplications(userId, status, applicationType, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("내 신청 목록을 조회했습니다.", response)
        );
    }
}