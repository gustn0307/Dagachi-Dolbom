package com.dagachi.backend.common.ai.dto;

import java.util.List;

public record AiCarePriorityResponse(
        List<Recommendation> recommendations,
        String model
) {
    public record Recommendation(
            String candidateKey,
            String riskLevel,
            int score,
            List<String> reasons,
            String recommendedAction
    ) {
    }
}
