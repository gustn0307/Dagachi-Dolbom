package com.dagachi.backend.institution.dashboard.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.institution.dashboard.dto.DashboardPeriod;
import com.dagachi.backend.institution.dashboard.dto.InstitutionDashboardResponse;
import com.dagachi.backend.institution.dashboard.dto.CarePriorityResponse;
import com.dagachi.backend.institution.dashboard.service.InstitutionCarePriorityService;
import com.dagachi.backend.institution.dashboard.service.InstitutionDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/institution/dashboard")
public class InstitutionDashboardController {

    private final InstitutionDashboardService dashboardService;
    private final InstitutionCarePriorityService carePriorityService;

    public InstitutionDashboardController(
            InstitutionDashboardService dashboardService,
            InstitutionCarePriorityService carePriorityService
    ) {
        this.dashboardService = dashboardService;
        this.carePriorityService = carePriorityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<InstitutionDashboardResponse>> getDashboard(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "MONTHLY") DashboardPeriod period
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "기관 대시보드를 조회했습니다.",
                dashboardService.getDashboard(userId, period)
        ));
    }

    @GetMapping("/care-priorities")
    public ResponseEntity<ApiResponse<CarePriorityResponse>> getCarePriorities(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 돌봄 우선순위를 분석했습니다.",
                carePriorityService.analyze(userId)
        ));
    }
}
