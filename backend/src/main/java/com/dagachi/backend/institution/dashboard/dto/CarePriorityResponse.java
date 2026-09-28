package com.dagachi.backend.institution.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CarePriorityResponse(
        LocalDateTime analyzedAt,
        String model,
        List<Item> items
) {
    public record Item(
            Long recipientId,
            String recipientName,
            String riskLevel,
            int score,
            List<String> reasons,
            String recommendedAction
    ) {
    }
}
