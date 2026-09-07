package com.dagachi.backend.user.record.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.record.dto.ActivityRecordResponse;
import com.dagachi.backend.user.record.service.ActivityRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    public ActivityRecordController(ActivityRecordService activityRecordService) {
        this.activityRecordService = activityRecordService;
    }

    /**
     * RECORD-01 활동 시작.
     *
     * POST /api/activities/{activityId}/start
     */
    @PostMapping("/api/activities/{activityId}/start")
    public ResponseEntity<ApiResponse<ActivityRecordResponse>> start(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ActivityRecordResponse response = activityRecordService.startActivity(activityId, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("활동이 시작되었습니다.", response));
    }
}