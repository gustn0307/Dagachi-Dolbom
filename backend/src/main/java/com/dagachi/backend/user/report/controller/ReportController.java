package com.dagachi.backend.user.report.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.report.dto.ReportCreateRequest;
import com.dagachi.backend.user.report.dto.ReportCreateResponse;
import com.dagachi.backend.user.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReportCreateResponse>> createReport(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestPart("request") ReportCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ReportCreateResponse response =
                reportService.createReport(userId, request, images);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "제보가 접수되었습니다.",
                        response
                ));
    }
}