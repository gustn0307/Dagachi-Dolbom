package com.dagachi.backend.institution.report.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.institution.report.dto.ReportAssignmentResponse;
import com.dagachi.backend.institution.report.service.InstitutionReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/institution/reports")
public class InstitutionReportController {

    private final InstitutionReportService institutionReportService;

    public InstitutionReportController(
            InstitutionReportService institutionReportService
    ) {
        this.institutionReportService = institutionReportService;
    }

    /**
     * 미배정 제보를 현재 로그인한 기관 사용자의 소속 기관에 배정합니다.
     *
     * institutionId를 Request로 직접 받지 않고,
     * JWT에서 확인된 userId의 소속 기관을 사용합니다.
     * 따라서 클라이언트가 다른 기관 ID를 임의로 지정할 수 없습니다.
     */
    @PatchMapping("/{reportId}/assignment")
    public ResponseEntity<ApiResponse<ReportAssignmentResponse>> assignReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId
    ) {
        ReportAssignmentResponse response =
                institutionReportService.assignReportToMyInstitution(
                        userId,
                        reportId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "제보가 기관에 배정되었습니다.",
                        response
                )
        );
    }
}