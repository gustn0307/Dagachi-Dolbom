package com.dagachi.backend.institution.activity.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.institution.activity.dto.InstitutionActivitySummaryResponse;
import com.dagachi.backend.institution.activity.service.InstitutionActivityService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 기관 담당자의 돌봄 활동 관리 API를 제공하는 Controller.
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
     * ACT-04 기관 활동 목록 조회.
     *
     * GET /api/institution/activities
     *
     * 지원 요청값:
     * - status: 활동 상태
     * - recipientId: 돌봄 대상자 ID
     * - dateFrom: 활동 조회 시작일
     * - dateTo: 활동 조회 종료일
     * - page: 페이지 번호, 0부터 시작
     * - size: 한 페이지의 활동 수
     *
     * 기본 정렬:
     * - 활동 예정일 최신순
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
            LocalDate dateFrom,

            @RequestParam(required = false)
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
}