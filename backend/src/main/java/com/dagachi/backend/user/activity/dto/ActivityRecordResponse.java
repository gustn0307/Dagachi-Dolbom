package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.VisitResult;

import java.time.LocalDateTime;
import java.util.List;

// RECORD-02/03/05 활동기록 조회 및 저장 결과 응답 DTO
public record ActivityRecordResponse(

        Long recordId,
        Long activityId,
        Integer checklistVersion,

        VisitResult visitResult,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String specialNote,

        LocalDateTime updatedAt,

        List<ChecklistAnswerResponse> responses,

        boolean signatureUploaded,

        ActivityReviewStatus reviewStatus,
        String reviewNote
) {

    // 저장된 체크리스트 문항의 질문 내용과 답변을 반환합니다.
    public record ChecklistAnswerResponse(
            Long checklistItemId,
            String question,
            String selectedValue,
            String textValue
    ) {
    }
}