package com.dagachi.backend.admin.notice.controller;

import com.dagachi.backend.admin.notice.dto.PublicNoticeResponse;
import com.dagachi.backend.admin.notice.service.PublicNoticeService;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.common.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class PublicNoticeController {

    private final PublicNoticeService publicNoticeService;

    // PublicNoticeService 의존성 주입
    public PublicNoticeController(
            PublicNoticeService publicNoticeService
    ) {
        this.publicNoticeService = publicNoticeService;
    }

    // 공개 공지 목록 조회 기능
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PublicNoticeResponse>>> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        PageResponse<PublicNoticeResponse> response =
                publicNoticeService.getNotices(
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 공개 공지 상세 조회 기능
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<PublicNoticeResponse>> getNotice(
            @PathVariable Long noticeId
    ) {
        PublicNoticeResponse response =
                publicNoticeService.getNotice(noticeId);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }
}