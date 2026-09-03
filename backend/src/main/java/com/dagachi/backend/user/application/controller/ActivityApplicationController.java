package com.dagachi.backend.user.application.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
import com.dagachi.backend.user.application.service.ActivityApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 일반 USER의 활동 직접 신청(APP-01) API를 제공한다.
 */
@RestController
@RequestMapping("/api/activities")
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
    @PostMapping("/{activityId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ApplicationResponse response = activityApplicationService.applyDirect(activityId, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("활동 신청이 접수되었습니다.", response));
    }
}