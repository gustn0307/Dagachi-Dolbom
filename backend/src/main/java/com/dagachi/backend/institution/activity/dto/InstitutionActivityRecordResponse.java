package com.dagachi.backend.institution.activity.dto;

import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.ChecklistItem;
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.VisitResult;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record InstitutionActivityRecordResponse(

        Long recordId,
        Long activityId,

        Long submittedById,
        String submittedByName,

        VisitResult visitResult,

        LocalDateTime startedAt,
        LocalDateTime completedAt,

        String specialNote,

        boolean signatureUploaded,

        ActivityReviewStatus reviewStatus,
        String reviewNote,

        Long reviewedById,
        String reviewedByName,
        LocalDateTime reviewedAt,

        List<ChecklistAnswerResponse> responses
) {

    public static InstitutionActivityRecordResponse from(
            ActivityRecord record,
            List<ChecklistResponse> checklistResponses
    ) {
        List<ChecklistAnswerResponse> responses =
                checklistResponses
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        response ->
                                                response
                                                        .getChecklistItem()
                                                        .getSortOrder()
                                )
                        )
                        .map(
                                ChecklistAnswerResponse::from
                        )
                        .toList();

        return new InstitutionActivityRecordResponse(
                record.getId(),
                record.getActivity().getId(),

                record.getSubmittedBy() == null
                        ? null
                        : record.getSubmittedBy().getId(),

                record.getSubmittedBy() == null
                        ? null
                        : record.getSubmittedBy().getName(),

                record.getVisitResult(),
                record.getStartedAt(),
                record.getCompletedAt(),
                record.getSpecialNote(),

                record.getSignatureS3Key() != null,

                record.getReviewStatus(),
                record.getReviewNote(),

                record.getReviewedBy() == null
                        ? null
                        : record.getReviewedBy().getId(),

                record.getReviewedBy() == null
                        ? null
                        : record.getReviewedBy().getName(),

                record.getReviewedAt(),
                responses
        );
    }

    public record ChecklistAnswerResponse(

            Long checklistItemId,
            String question,
            String selectedValue,
            String textValue,
            Integer sortOrder
    ) {

        public static ChecklistAnswerResponse from(
                ChecklistResponse response
        ) {
            ChecklistItem item =
                    response.getChecklistItem();

            return new ChecklistAnswerResponse(
                    item.getId(),
                    item.getQuestion(),
                    response.getSelectedValue(),
                    response.getTextValue(),
                    item.getSortOrder()
            );
        }
    }
}