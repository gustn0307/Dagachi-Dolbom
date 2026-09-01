package com.dagachi.backend.user.activity.controller;

import com.dagachi.backend.common.response.ApiResponse;
import com.dagachi.backend.user.activity.dto.ActivityChecklistResponse;
import com.dagachi.backend.user.activity.service.ActivityChecklistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity-records")
public class ActivityChecklistController {

    private final ActivityChecklistService activityChecklistService;

    public ActivityChecklistController(
            ActivityChecklistService activityChecklistService
    ) {
        this.activityChecklistService = activityChecklistService;
    }

    // CHECK-01 활동 기록의 체크리스트 문항과 기존 응답을 조회한다.
    @GetMapping("/{recordId}/checklist")
    public ResponseEntity<ApiResponse<ActivityChecklistResponse>> getChecklist(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Long userId
    ) {
        // 1. URL에서 받은 recordId와 로그인한 userId를 Service에 전달
        ActivityChecklistResponse response =
                activityChecklistService.getChecklist(
                        recordId,
                        userId
                );

        // 2. Service 결과를 프로젝트 공통 ApiResponse 형식으로 감싸 HTTP 200으로 반환
        return ResponseEntity.ok(
                ApiResponse.success(
                        "체크리스트를 조회했습니다.",
                        response
                )
        );
    }
}