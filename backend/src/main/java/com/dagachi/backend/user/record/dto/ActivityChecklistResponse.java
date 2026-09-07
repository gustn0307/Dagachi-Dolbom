package com.dagachi.backend.user.record.dto;

import java.util.List;

public record ActivityChecklistResponse(
        Integer checklistVersion,
        List<ChecklistItemResponse> items
) {
}