package com.dagachi.backend.institution.report.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.institution.report.dto.*;
import com.dagachi.backend.institution.report.service.InstitutionReportService;
import com.dagachi.backend.institution.report.service.ReportAiAnalysisService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.dagachi.backend.institution.recipient.dto.CareRecipientCreateRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/institution/reports")
public class InstitutionReportController {

    private final InstitutionReportService institutionReportService;
    private final ReportAiAnalysisService reportAiAnalysisService;

    public InstitutionReportController(
            InstitutionReportService institutionReportService,
            ReportAiAnalysisService reportAiAnalysisService
    ) {
        this.institutionReportService = institutionReportService;
        this.reportAiAnalysisService = reportAiAnalysisService;
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

    /**
     * 현재 로그인한 기관에 배정된 제보 목록을 조회합니다.
     *
     * 기존 REPORT-03 API이며,
     * 다른 기관에 배정된 제보와 미배정 제보는 포함하지 않습니다.
     *
     * 날짜 조건은 yyyy-MM-dd 형식으로 받습니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InstitutionReportListItemResponse>>>
    getInstitutionReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            Pageable pageable
    ) {
        PageResponse<InstitutionReportListItemResponse> response =
                institutionReportService.getInstitutionReports(
                        userId,
                        status,
                        from,
                        to,
                        pageable
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 제보 목록을 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 아직 어떤 기관에도 배정되지 않은 제보 목록을 조회합니다.
     *
     * 로그인 기관의 주소를 기준으로 제보 위치와의 거리를 계산하여
     * 가까운 제보부터 반환합니다.
     *
     * 미배정 상태에서는 여러 기관이 조회할 수 있으므로
     * 응답 DTO에는 정확한 주소나 제보자 개인정보를 포함하지 않습니다.
     */
    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<PageResponse<UnassignedReportListItemResponse>>>
    getUnassignedReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            Pageable pageable
    ) {
        PageResponse<UnassignedReportListItemResponse> response =
                institutionReportService.getUnassignedReports(
                        userId,
                        status,
                        from,
                        to,
                        pageable
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "미배정 제보 목록을 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 특정 제보의 상세 정보를 조회합니다.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<InstitutionReportDetailResponse>>
    getInstitutionReportDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId
    ) {
        InstitutionReportDetailResponse response =
                institutionReportService.getInstitutionReportDetail(
                        userId,
                        reportId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 제보 상세를 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 제보의 AI 요약을 생성합니다.
     *
     * Spring Boot가 기관 범위와 Report를 검증한 뒤
     * 제보 원문(content)만 FastAPI에 전달합니다.
     *
     * 생성된 AI 결과는 ai_analyses에 저장한 뒤
     * 외부 API DTO로 반환합니다.
     */
    @PostMapping("/{reportId}/ai-analyses")
    public ResponseEntity<ApiResponse<ReportAiAnalysisResponse>>
    createReportSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId
    ) {
        ReportAiAnalysisResponse response =
                reportAiAnalysisService.createReportSummary(
                        userId,
                        reportId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "제보 AI 요약을 생성했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 제보의
     * 최신 AI 요약 결과를 조회합니다.
     */
    @GetMapping("/{reportId}/ai-analyses")
    public ResponseEntity<ApiResponse<ReportAiAnalysisResponse>>
    getLatestReportSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId
    ) {
        ReportAiAnalysisResponse response =
                reportAiAnalysisService.getLatestReportSummary(
                        userId,
                        reportId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "제보 AI 요약 결과를 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 제보의 처리 상태를 변경합니다.
     *
     * 상태 전이 가능 여부는 Report Entity의 도메인 규칙으로 검증하며,
     * 다른 기관의 제보 또는 미배정 제보는 변경할 수 없습니다.
     */
    @PatchMapping("/{reportId}/status")
    public ResponseEntity<ApiResponse<ReportStatusUpdateResponse>>
    updateReportStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId,
            @Valid @RequestBody ReportStatusUpdateRequest request
    ) {
        ReportStatusUpdateResponse response =
                institutionReportService.updateReportStatus(
                        userId,
                        reportId,
                        request.status()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "제보 상태를 변경했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관의 제보에
     * 같은 기관의 기존 돌봄 대상자를 연결합니다.
     */
    @PutMapping("/{reportId}/care-recipient")
    public ResponseEntity<ApiResponse<ReportCareRecipientLinkResponse>>
    linkCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId,
            @Valid @RequestBody ReportCareRecipientLinkRequest request
    ) {
        ReportCareRecipientLinkResponse response =
                institutionReportService.linkCareRecipient(
                        userId,
                        reportId,
                        request.careRecipientId()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "제보에 돌봄 대상자를 연결했습니다.",
                        response
                )
        );
    }

    /**
     * 현재 로그인한 기관의 제보를 기준으로
     * 신규 돌봄 대상자를 생성하고 즉시 해당 제보에 연결합니다.
     *
     * CARE-03과 동일한 대상자 입력 필드를 사용합니다.
     */
    @PostMapping("/{reportId}/care-recipient")
    public ResponseEntity<ApiResponse<ReportCareRecipientCreateResponse>>
    createAndLinkCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId,
            @Valid @RequestBody CareRecipientCreateRequest request
    ) {
        ReportCareRecipientCreateResponse response =
                institutionReportService.createAndLinkCareRecipient(
                        userId,
                        reportId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "돌봄 대상자를 등록하고 제보에 연결했습니다.",
                                response
                        )
                );
    }
}