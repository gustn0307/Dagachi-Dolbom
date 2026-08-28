package com.dagachi.backend.user.report.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.user.report.dto.ReportListItemResponse;
import com.dagachi.backend.user.report.service.ReportService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/reports")
public class UserReportController {

    private final ReportService reportService;

    public UserReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReportListItemResponse>>> getMyReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ReportStatus status,
            Pageable pageable
    ) {
        PageResponse<ReportListItemResponse> response =
                reportService.getMyReports(userId, status, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "내 제보 목록을 조회했습니다.",
                        response
                )
        );
    }
}