package com.dagachi.backend.user.activity.dto;

import com.dagachi.backend.domain.enums.ChecklistItemType;
import java.util.List;

public record ChecklistItemResponse(
        Long id,
        String code,
        String question,
        ChecklistItemType itemType,
        List<String> options,
        Boolean required,
        Integer sortOrder,
        String selectedValue,
        String textValue
) {
}