package com.dagachi.backend.stats.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.stats.dto.PlatformSummaryResponse;
import com.dagachi.backend.stats.service.PlatformStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class PlatformStatsController {

    private final PlatformStatsService platformStatsService;

    public PlatformStatsController(PlatformStatsService platformStatsService) {
        this.platformStatsService = platformStatsService;
    }

    // GET /api/stats/summary
    // 홈 화면 하단에 노출할 서비스 참여 현황 집계 (비회원도 접근 가능해야 함 → SecurityConfig permitAll 필요)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PlatformSummaryResponse>> getPlatformSummary() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "서비스 참여 현황을 조회했습니다.",
                        platformStatsService.getPlatformSummary()
                )
        );
    }
}