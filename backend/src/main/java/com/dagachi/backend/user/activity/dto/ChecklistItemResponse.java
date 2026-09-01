package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.domain.enums.ChecklistItemType;
import com.fasterxml.jackson.databind.JsonNode;

public record ChecklistItemResponse(
        Long id,
        String code,
        String question,
        ChecklistItemType itemType,
        JsonNode options,
        Boolean required,
        Integer sortOrder,
        String selectedValue,
        String textValue
) {
}