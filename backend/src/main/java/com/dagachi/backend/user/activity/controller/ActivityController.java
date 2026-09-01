package com.dagachi.backend.user.activity.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.user.activity.dto.ActivityDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityExecutionDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityResponse;
import com.dagachi.backend.user.activity.dto.ActivitySearchCondition;
import com.dagachi.backend.user.activity.service.ActivityService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 일반 USER의 모집 활동 탐색(목록·상세·수행정보) API를 제공한다.
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * ACT-01 모집 활동 목록 조회.
     *
     * GET /api/activities
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ActivityResponse>>> getActivities(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) List<String> ageGroups,
            @RequestParam(required = false) String gender,
            @PageableDefault(page = 0, size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        ActivitySearchCondition condition =
                new ActivitySearchCondition(latitude, longitude, region, dateFrom, dateTo, ageGroups, gender);

        PageResponse<ActivityResponse> response = activityService.getActivities(condition, pageable);

        return ResponseEntity.ok(
                ApiResponse.success("활동 목록을 조회했습니다.", response)
        );
    }

    /**
     * ACT-02 모집 활동 상세 조회.
     *
     * GET /api/activities/{activityId}
     */
    @GetMapping("/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDetailResponse>> getActivityDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ActivityDetailResponse response = activityService.getActivityDetail(activityId, userId);

        return ResponseEntity.ok(
                ApiResponse.success("활동 상세를 조회했습니다.", response)
        );
    }

    /**
     * ACT-03 승인 참여자 수행정보 조회.
     *
     * GET /api/activities/{activityId}/execution-details
     */
    @GetMapping("/{activityId}/execution-details")
    public ResponseEntity<ApiResponse<ActivityExecutionDetailResponse>> getExecutionDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ActivityExecutionDetailResponse response =
                activityService.getExecutionDetail(activityId, userId);

        return ResponseEntity.ok(
                ApiResponse.success("활동 수행정보를 조회했습니다.", response)
        );
    }
}