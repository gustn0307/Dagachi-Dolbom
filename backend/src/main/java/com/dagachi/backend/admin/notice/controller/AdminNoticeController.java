package com.dagachi.backend.admin.notice.controller;

import com.dagachi.backend.admin.notice.service.AdminNoticeService;
import com.dagachi.backend.admin.notice.dto.AdminNoticeResponse;
import com.dagachi.backend.admin.notice.dto.AdminNoticeCreateRequest;
import com.dagachi.backend.admin.notice.dto.AdminNoticeUpdateRequest;
import com.dagachi.backend.domain.enums.NoticeStatus;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    // AdminNoticeService 의존성 주입
    public AdminNoticeController(
            AdminNoticeService adminNoticeService
    ) {
        this.adminNoticeService = adminNoticeService;
    }

    // 관리자 공지 목록 조회 기능
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminNoticeResponse>>> getNotices(
            @RequestParam(required = false) NoticeStatus status,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        PageResponse<AdminNoticeResponse> response =
                adminNoticeService.getNotices(
                        status,
                        deleted,
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 관리자 공지 등록 기능
    @PostMapping
    public ResponseEntity<ApiResponse<AdminNoticeResponse>> createNotice(
            @Valid @RequestBody AdminNoticeCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        AdminNoticeResponse response =
                adminNoticeService.createNotice(
                        request,
                        userId
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 관리자 공지 수정 및 상태 변경 기능
    @PatchMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeResponse>> updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody AdminNoticeUpdateRequest request
    ) {
        AdminNoticeResponse response =
                adminNoticeService.updateNotice(
                        noticeId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 관리자 공지 Soft Delete 기능
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(
            @PathVariable Long noticeId
    ) {
        adminNoticeService.deleteNotice(noticeId);

        return ResponseEntity.ok(
                ApiResponse.success()
        );
    }
}