package com.dagachi.backend.institution.recipient.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.institution.recipient.dto.CareRecipientConsentRequest;
import com.dagachi.backend.institution.recipient.dto.CareRecipientCreateRequest;
import com.dagachi.backend.institution.recipient.dto.CareRecipientDetailResponse;
import com.dagachi.backend.institution.recipient.dto.CareRecipientSummaryResponse;
import com.dagachi.backend.institution.recipient.dto.CareRecipientUpdateRequest;
import com.dagachi.backend.institution.recipient.service.CareRecipientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

/**
 * 기관 담당자의 돌봄 대상자 조회, 등록 및 수정 API를 제공한다.
 *
 * Controller는 HTTP 요청을 받고 Service에 전달한다.
 * 기관 범위 검증과 실제 비즈니스 로직은 Service에서 처리한다.
 */
@RestController
@RequestMapping("/api/institution/care-recipients")
public class CareRecipientController {

    private final CareRecipientService careRecipientService;

    public CareRecipientController(
            CareRecipientService careRecipientService
    ) {
        this.careRecipientService = careRecipientService;
    }

    /**
     * CARE-01 기관 돌봄 대상자 목록 조회.
     *
     * GET /api/institution/care-recipients
     */
    @GetMapping
    public ResponseEntity<
            ApiResponse<
                    PageResponse<CareRecipientSummaryResponse>
                    >
            >
    getCareRecipients(
            @AuthenticationPrincipal Long userId,

            @RequestParam(required = false)
            CareRecipientStatus status,

            @RequestParam(required = false)
            ConsentStatus consentStatus,

            @RequestParam(required = false)
            String keyword,

            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<CareRecipientSummaryResponse> response =
                careRecipientService.getCareRecipients(
                        userId,
                        status,
                        consentStatus,
                        keyword,
                        pageable
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자 목록을 조회했습니다.",
                        response
                )
        );
    }

    /**
     * CARE-02 기관 돌봄 대상자 상세 조회.
     *
     * GET /api/institution/care-recipients/{recipientId}
     */
    @GetMapping("/{recipientId}")
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    getCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.getCareRecipient(
                        userId,
                        recipientId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자 상세 정보를 조회했습니다.",
                        response
                )
        );
    }

    /**
     * CARE-03 기관 돌봄 대상자 등록.
     *
     * POST /api/institution/care-recipients
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    createCareRecipient(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CareRecipientCreateRequest request
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.createCareRecipient(
                        userId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "돌봄 대상자가 등록되었습니다.",
                                response
                        )
                );
    }

    /**
     * CARE-04 기관 돌봄 대상자 기본정보 수정.
     *
     * PATCH /api/institution/care-recipients/{recipientId}
     */
    @PatchMapping("/{recipientId}")
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    updateCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId,
            @Valid @RequestBody CareRecipientUpdateRequest request
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.updateCareRecipient(
                        userId,
                        recipientId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자 정보가 수정되었습니다.",
                        response
                )
        );
    }
    /**
     * CARE-05 기관 돌봄 대상자 동의 상태 변경.
     *
     * PATCH /api/institution/care-recipients/{recipientId}/consent
     */
    @PatchMapping("/{recipientId}/consent")
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    updateConsentStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId,
            @Valid @RequestBody CareRecipientConsentRequest request
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.updateConsentStatus(
                        userId,
                        recipientId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자의 동의 상태가 변경되었습니다.",
                        response
                )
        );
    }
    /**
     * CARE-06 기관 돌봄 대상자 관리 종료.
     *
     * POST /api/institution/care-recipients/{recipientId}/close
     *
     * 대상자를 실제 삭제하지 않고 상태를 INACTIVE로 변경한다.
     */
    @PostMapping("/{recipientId}/close")
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    closeCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.closeCareRecipient(
                        userId,
                        recipientId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자 관리가 종료되었습니다.",
                        response
                )
        );
    }
    /**
     * CARE-07 돌봄 대상자 관리 재개.
     *
     * POST /api/institution/care-recipients/{recipientId}/reopen
     *
     * 종료된 대상자를 새로 등록하지 않고
     * 기존 대상자의 관리 상태를 ACTIVE로 변경한다.
     */
    @PostMapping("/{recipientId}/reopen")
    public ResponseEntity<ApiResponse<CareRecipientDetailResponse>>
    reopenCareRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId
    ) {
        CareRecipientDetailResponse response =
                careRecipientService.reopenCareRecipient(
                        userId,
                        recipientId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "돌봄 대상자 관리가 재개되었습니다.",
                        response
                )
        );
    }
}