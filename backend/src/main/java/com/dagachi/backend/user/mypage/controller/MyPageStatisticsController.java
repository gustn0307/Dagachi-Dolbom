package com.dagachi.backend.user.mypage.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.mypage.dto.ActivityStatisticsResponse;
import com.dagachi.backend.user.mypage.service.MyPageStatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * STAT-01 내 활동 통계 API.
 */
@RestController
@RequestMapping("/api/users/me")
public class MyPageStatisticsController {

    private final MyPageStatisticsService myPageStatisticsService;

    public MyPageStatisticsController(MyPageStatisticsService myPageStatisticsService) {
        this.myPageStatisticsService = myPageStatisticsService;
    }

    /**
     * GET /api/users/me/activity-statistics
     */
    @GetMapping("/activity-statistics")
    public ResponseEntity<ApiResponse<ActivityStatisticsResponse>> getMyActivityStatistics(
            @AuthenticationPrincipal Long userId
    ) {
        ActivityStatisticsResponse response = myPageStatisticsService.getMyActivityStatistics(userId);

        return ResponseEntity.ok(
                ApiResponse.success("내 활동 통계를 조회했습니다.", response)
        );
    }
}