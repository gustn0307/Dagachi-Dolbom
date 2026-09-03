package com.dagachi.backend.institution.activity.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.institution.activity.dto.*;
import com.dagachi.backend.institution.activity.service.InstitutionActivityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 기관 담당자의 활동 관리 API를 제공하는 Controller.
 */
@RestController
@RequestMapping("/api/institution/activities")
public class InstitutionActivityController {

    private final InstitutionActivityService
            institutionActivityService;

    public InstitutionActivityController(
            InstitutionActivityService institutionActivityService
    ) {
        this.institutionActivityService =
                institutionActivityService;
    }

    /**
     * 기관 활동 목록 조회.
     *
     * GET /api/institution/activities
     */
    @GetMapping
    public ResponseEntity<
            ApiResponse<
                    PageResponse<InstitutionActivitySummaryResponse>
                    >
            >
    getInstitutionActivities(
            @AuthenticationPrincipal
            Long userId,

            @RequestParam(required = false)
            ActivityStatus status,

            @RequestParam(required = false)
            Long recipientId,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate dateTo,

            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "scheduledAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<InstitutionActivitySummaryResponse> response =
                institutionActivityService
                        .getInstitutionActivities(
                                userId,
                                status,
                                recipientId,
                                dateFrom,
                                dateTo,
                                pageable
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동 목록을 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 기관 활동 등록.
     *
     * POST /api/institution/activities
     */
    @PostMapping
    public ResponseEntity<
            ApiResponse<InstitutionActivityDetailResponse>
            >
    createInstitutionActivity(
            @AuthenticationPrincipal
            Long userId,

            @Valid
            @RequestBody
            InstitutionActivityCreateRequest request
    ) {
        InstitutionActivityDetailResponse response =
                institutionActivityService
                        .createInstitutionActivity(
                                userId,
                                request
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동을 등록했습니다.",
                        response
                )
        );
    }

    /**
     * 기관 활동 상세 조회.
     *
     * GET /api/institution/activities/{activityId}
     */
    @GetMapping("/{activityId}")
    public ResponseEntity<
            ApiResponse<InstitutionActivityDetailResponse>
            >
    getInstitutionActivity(
            @AuthenticationPrincipal
            Long userId,

            @PathVariable
            Long activityId
    ) {
        InstitutionActivityDetailResponse response =
                institutionActivityService
                        .getInstitutionActivity(
                                userId,
                                activityId
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동 상세 정보를 조회했습니다.",
                        response
                )
        );
    }

    /**
     * 기관 활동 정보 수정.
     *
     * PATCH /api/institution/activities/{activityId}
     */
    @PatchMapping("/{activityId}")
    public ResponseEntity<
            ApiResponse<InstitutionActivityDetailResponse>
            >
    updateInstitutionActivity(
            @AuthenticationPrincipal
            Long userId,

            @PathVariable
            Long activityId,

            @Valid
            @RequestBody
            InstitutionActivityUpdateRequest request
    ) {
        InstitutionActivityDetailResponse response =
                institutionActivityService
                        .updateInstitutionActivity(
                                userId,
                                activityId,
                                request
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동 정보를 수정했습니다.",
                        response
                )
        );
    }

    /**
     * 기관 활동 상태 변경.
     *
     * PATCH /api/institution/activities/{activityId}/status
     */
    @PatchMapping("/{activityId}/status")
    public ResponseEntity<
            ApiResponse<InstitutionActivityDetailResponse>
            >
    changeInstitutionActivityStatus(
            @AuthenticationPrincipal
            Long userId,

            @PathVariable
            Long activityId,

            @Valid
            @RequestBody
            InstitutionActivityStatusRequest request
    ) {
        InstitutionActivityDetailResponse response =
                institutionActivityService
                        .changeInstitutionActivityStatus(
                                userId,
                                activityId,
                                request
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동 상태를 변경했습니다.",
                        response
                )
        );
    }

    /**
     * 기관 활동 신청자 목록 조회.
     *
     * GET /api/institution/activities/{activityId}/applications
     *
     * status를 전달하면 해당 신청 상태만 조회한다.
     */
    @GetMapping("/{activityId}/applications")
    public ResponseEntity<
            ApiResponse<
                    PageResponse<InstitutionActivityApplicationResponse>
                    >
            >
    getInstitutionActivityApplications(
            @AuthenticationPrincipal
            Long userId,

            @PathVariable
            Long activityId,

            @RequestParam(required = false)
            ApplicationStatus status,

            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<InstitutionActivityApplicationResponse> response =
                institutionActivityService
                        .getInstitutionActivityApplications(
                                userId,
                                activityId,
                                status,
                                pageable
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 활동 신청자 목록을 조회했습니다.",
                        response
                )
        );
    }
}