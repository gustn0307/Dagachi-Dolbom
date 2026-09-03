package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.domain.enums.VisitResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

// RECORD-03 공동 Draft 저장 요청 DTO
public record ActivityRecordDraftRequest(

        VisitResult visitResult,

        LocalDateTime completedAt,

        String specialNote,

        @NotNull(message = "체크리스트 응답 목록은 필수입니다.")
        @Valid
        List<ChecklistAnswerRequest> responses

) {

    // Draft에 저장할 체크리스트 문항별 응답
    public record ChecklistAnswerRequest(

            @NotNull(message = "체크리스트 문항 ID는 필수입니다.")
            Long itemId,

            String selectedValue,

            String textValue
    ) {
    }
}