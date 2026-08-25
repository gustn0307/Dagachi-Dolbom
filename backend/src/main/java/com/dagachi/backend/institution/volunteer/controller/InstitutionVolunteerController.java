package com.dagachi.backend.institution.volunteer.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerOverviewResponse;
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
 *
 * Controller는 HTTP 요청을 받고 로그인 사용자 ID와
 * 요청값을 Service에 전달한 뒤 공통 응답 형태로 반환한다.
 */
@RestController
@RequestMapping("/api/institution/volunteers")
public class InstitutionVolunteerController {

    private final InstitutionVolunteerService
            institutionVolunteerService;

    /**
     * 생성자 주입으로 봉사자 관리 Service를 전달받는다.
     */
    public InstitutionVolunteerController(
            InstitutionVolunteerService institutionVolunteerService
    ) {
        this.institutionVolunteerService =
                institutionVolunteerService;
    }

    /**
     * VOL-01~03 기관 봉사자 목록, 검색 및 정렬.
     *
     * GET /api/institution/volunteers
     *
     * 지원 요청값:
     * - keyword: 이름, 닉네임, 전화번호 검색
     * - sortType: recent 또는 participation
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
            @AuthenticationPrincipal
            Long userId,

            // 이름, 닉네임 또는 전화번호 검색어
            @RequestParam(required = false)
            String keyword,

            // recent: 최근 활동순
            // participation: 참여 횟수 많은 순
            @RequestParam(
                    required = false,
                    defaultValue = "recent"
            )
            String sortType,

            // 기본값은 첫 페이지, 한 페이지당 20명이다.
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
                                sortType,
                                pageable
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 봉사자 목록을 조회했습니다.",
                        response
                )
        );
    }

    /**
     * VOL-05 기관 봉사자 현황 요약 조회.
     *
     * GET /api/institution/volunteers/summary
     *
     * 다음 정보를 반환한다.
     * - 전체 봉사자 수
     * - 현재 활동 중인 봉사자 수
     * - 참여 예정 봉사자 수
     */
    @GetMapping("/summary")
    public ResponseEntity<
            ApiResponse<InstitutionVolunteerOverviewResponse>
            >
    getInstitutionVolunteerOverview(
            @AuthenticationPrincipal
            Long userId
    ) {
        InstitutionVolunteerOverviewResponse response =
                institutionVolunteerService
                        .getInstitutionVolunteerOverview(
                                userId
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 봉사자 현황을 조회했습니다.",
                        response
                )
        );
    }
}