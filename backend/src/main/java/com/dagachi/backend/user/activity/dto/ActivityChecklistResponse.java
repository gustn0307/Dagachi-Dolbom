package com.dagachi.backend.user.activity.dto;

import java.util.List;

public record ActivityChecklistResponse(
        Integer checklistVersion,
        List<ChecklistItemResponse> items
) {
}