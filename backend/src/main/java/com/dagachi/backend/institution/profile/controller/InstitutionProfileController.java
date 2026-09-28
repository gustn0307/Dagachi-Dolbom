package com.dagachi.backend.institution.profile.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.institution.profile.dto.InstitutionProfileResponse;
import com.dagachi.backend.institution.profile.service.InstitutionProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/institution/profile")
public class InstitutionProfileController {

    private final InstitutionProfileService institutionProfileService;

    public InstitutionProfileController(InstitutionProfileService institutionProfileService) {
        this.institutionProfileService = institutionProfileService;
    }

    // GET /api/institution/profile
    // 로그인한 기관 담당자(INSTITUTION) 본인의 소속 기관 정보를 조회한다.
    @GetMapping
    public ResponseEntity<ApiResponse<InstitutionProfileResponse>> getMyInstitutionProfile(
            @AuthenticationPrincipal Long userId
    ) {
        InstitutionProfileResponse response =
                institutionProfileService.getMyInstitutionProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "기관 정보를 조회했습니다.",
                        response
                )
        );
    }
}