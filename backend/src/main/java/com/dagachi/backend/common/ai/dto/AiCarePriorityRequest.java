package com.dagachi.backend.common.ai.dto;

import java.util.List;

public record AiCarePriorityRequest(
        List<Candidate> candidates
) {
    public record Candidate(
            String candidateKey,
            Integer daysSinceLastCheck,
            long recentActivityCount,
            long mealConcernCount,
            long healthConcernCount,
            long supportNeededCount,
            boolean hasUpcomingActivity
    ) {
    }
}
