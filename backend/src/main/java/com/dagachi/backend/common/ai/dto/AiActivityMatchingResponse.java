package com.dagachi.backend.common.ai.dto;

import java.util.List;

/**
 * FastAPI 활동 AI 매칭 API의 응답입니다.
 */
public record AiActivityMatchingResponse(
        List<Recommendation> recommendations,
        String model
) {

    /**
     * AI가 후보 활동에 부여한 순위와 추천 이유입니다.
     */
    public record Recommendation(
            Long activityId,
            int rank,
            String reason
    ) {
    }
}