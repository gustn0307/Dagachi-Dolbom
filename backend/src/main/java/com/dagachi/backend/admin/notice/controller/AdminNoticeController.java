package com.dagachi.backend.admin.notice.controller;

import com.dagachi.backend.admin.notice.service.AdminNoticeService;
import com.dagachi.backend.admin.notice.dto.AdminNoticeResponse;
import com.dagachi.backend.admin.notice.dto.AdminNoticeCreateRequest;
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

@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    public AdminNoticeController(
            AdminNoticeService adminNoticeService
    ) {
        this.adminNoticeService = adminNoticeService;
    }

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
}