package com.dagachi.backend.institution.volunteer.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import com.dagachi.backend.institution.volunteer.service.InstitutionVolunteerService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기관 담당자의 봉사자 관리 API를 제공하는 Controller.
 */
@RestController
@RequestMapping("/api/institution/volunteers")
public class InstitutionVolunteerController {

    private final InstitutionVolunteerService
            institutionVolunteerService;

    public InstitutionVolunteerController(
            InstitutionVolunteerService institutionVolunteerService
    ) {
        this.institutionVolunteerService =
                institutionVolunteerService;
    }

    /**
     * VOL-01, VOL-02 기관 봉사자 목록 및 검색.
     *
     * GET /api/institution/volunteers
     *
     * 지원 요청값:
     * - keyword: 이름, 닉네임, 전화번호 검색
     * - page: 페이지 번호
     * - size: 한 페이지의 봉사자 수
     */
    @GetMapping
    public ResponseEntity<
            ApiResponse<
                    PageResponse<InstitutionVolunteerSummaryResponse>
                    >
            >
    getInstitutionVolunteers(
            @AuthenticationPrincipal Long userId,

            @RequestParam(required = false)
            String keyword,

            @PageableDefault(
                    page = 0,
                    size = 20
            )
            Pageable pageable
    ) {
        PageResponse<InstitutionVolunteerSummaryResponse> response =
                institutionVolunteerService
                        .getInstitutionVolunteers(
                                userId,
                                keyword,
                                pageable
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 봉사자 목록을 조회했습니다.",
                        response
                )
        );
    }
}