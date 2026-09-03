package com.dagachi.backend.user.activity.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.activity.dto.ActivityRecordResponse;
import com.dagachi.backend.user.activity.dto.ActivityRecordDraftRequest;
import com.dagachi.backend.user.activity.dto.ActivityRecordSignatureResponse;
import com.dagachi.backend.user.activity.service.ActivityRecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/activity-records")
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    public ActivityRecordController(
            ActivityRecordService activityRecordService
    ) {
        this.activityRecordService = activityRecordService;
    }

    // RECORD-02 승인된 참여자가 현재 활동기록과 저장된 체크리스트 응답을 조회합니다.
    @GetMapping("/{recordId}")
    public ResponseEntity<ApiResponse<ActivityRecordResponse>> getActivityRecord(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId
    ) {
        // recordId와 로그인 사용자 ID를 Service에 전달하여 활동기록을 조회합니다.
        ActivityRecordResponse response =
                activityRecordService.getActivityRecord(
                        recordId,
                        userId
                );

        // 조회 결과를 프로젝트 공통 ApiResponse 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록을 조회했습니다.",
                        response
                )
        );
    }

    // RECORD-03 승인된 참여자가 공동 Draft 활동기록을 저장합니다.
    @PutMapping("/{recordId}/draft")
    public ResponseEntity<ApiResponse<ActivityRecordResponse>> saveDraft(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ActivityRecordDraftRequest request
    ) {
        // 로그인 사용자와 Draft 요청값을 Service에 전달하여 활동기록을 저장합니다.
        ActivityRecordResponse response =
                activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                );

        // 저장된 최신 활동기록 상태를 공통 ApiResponse 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록 Draft를 저장했습니다.",
                        response
                )
        );
    }

    // RECORD-04 승인된 참여자가 MET 활동기록에 대상자 서명을 업로드하거나 교체합니다.
    @PostMapping("/{recordId}/signature")
    public ResponseEntity<ApiResponse<ActivityRecordSignatureResponse>> uploadSignature(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId,
            @RequestPart("signature") MultipartFile signature
    ) {
        // 로그인 사용자와 서명 파일을 Service에 전달하여 서명을 저장합니다.
        ActivityRecordSignatureResponse response =
                activityRecordService.uploadSignature(
                        recordId,
                        userId,
                        signature
                );

        // 서명 등록 결과를 공통 ApiResponse 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        "서명이 등록되었습니다.",
                        response
                )
        );
    }

    // RECORD-05 승인된 참여자가 활동기록을 최종 제출합니다.
    @PostMapping("/{recordId}/submit")
    public ResponseEntity<ApiResponse<ActivityRecordResponse>> submit(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId
    ) {
        // 로그인 사용자의 현재 Draft를 최종 검증하고 제출합니다.
        ActivityRecordResponse response =
                activityRecordService.submit(
                        recordId,
                        userId
                );

        // 제출 완료된 최신 활동기록 상태를 공통 ApiResponse 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        "활동기록을 제출했습니다.",
                        response
                )
        );
    }
}