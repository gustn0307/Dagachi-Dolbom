package com.dagachi.backend.user.record.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.record.dto.ActivityRecordDetailResponse;
import com.dagachi.backend.user.record.dto.ActivityRecordDraftRequest;
import com.dagachi.backend.user.record.dto.ActivityRecordResponse;
import com.dagachi.backend.user.record.dto.ActivityRecordSignatureResponse;
import com.dagachi.backend.user.record.service.ActivityRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    public ActivityRecordController(
            ActivityRecordService activityRecordService
    ) {
        this.activityRecordService = activityRecordService;
    }

    /**
     * RECORD-01 활동 시작.
     *
     * POST /api/activities/{activityId}/start
     */
    @PostMapping("/api/activities/{activityId}/start")
    public ResponseEntity<ApiResponse<ActivityRecordResponse>> start(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        ActivityRecordResponse response =
                activityRecordService.startActivity(
                        activityId,
                        userId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "활동이 시작되었습니다.",
                                response
                        )
                );
    }

    // RECORD-02 승인된 참여자가 현재 활동기록과 저장된 체크리스트 응답을 조회합니다.
    @GetMapping("/api/activity-records/{recordId}")
    public ResponseEntity<ApiResponse<ActivityRecordDetailResponse>> getActivityRecord(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId
    ) {
        ActivityRecordDetailResponse response =
                activityRecordService.getActivityRecord(
                        recordId,
                        userId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록을 조회했습니다.",
                        response
                )
        );
    }

    // RECORD-03 승인된 참여자가 공동 Draft 활동기록을 저장합니다.
    @PutMapping("/api/activity-records/{recordId}/draft")
    public ResponseEntity<ApiResponse<ActivityRecordDetailResponse>> saveDraft(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ActivityRecordDraftRequest request
    ) {
        ActivityRecordDetailResponse response =
                activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록 Draft를 저장했습니다.",
                        response
                )
        );
    }

    // RECORD-04 승인된 참여자가 MET 활동기록에 대상자 서명을 업로드하거나 교체합니다.
    @PostMapping("/api/activity-records/{recordId}/signature")
    public ResponseEntity<ApiResponse<ActivityRecordSignatureResponse>> uploadSignature(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId,
            @RequestPart("signature") MultipartFile signature
    ) {
        ActivityRecordSignatureResponse response =
                activityRecordService.uploadSignature(
                        recordId,
                        userId,
                        signature
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "서명이 등록되었습니다.",
                        response
                )
        );
    }

    // RECORD-05 승인된 참여자가 활동기록을 최종 제출합니다.
    @PostMapping("/api/activity-records/{recordId}/submit")
    public ResponseEntity<ApiResponse<ActivityRecordDetailResponse>> submit(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId
    ) {
        ActivityRecordDetailResponse response =
                activityRecordService.submit(
                        recordId,
                        userId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록을 제출했습니다.",
                        response
                )
        );
    }
}