package com.dagachi.backend.common.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * Spring Boot가 FastAPI 활동 AI 매칭 API로 전달하는 요청입니다.
 */
public record AiActivityMatchingRequest(
        Profile profile,
        List<Candidate> candidates
) {

    /**
     * 로그인 사용자의 승인 완료 활동 경험입니다.
     */
    public record Profile(
            long completedActivityCount,
            List<String> experiencedRegions,
            List<String> experiencedAgeGroups
    ) {
    }

    /**
     * Spring에서 1차 조건을 확인한 추천 후보 활동입니다.
     */
    public record Candidate(
            Long activityId,
            Double distanceKm,
            Integer daysSinceLastChecked,
            String ageGroup,
            String region,
            Integer requiredPeople,
            long approvedCount,
            List<RecentRecord> recentRecords
    ) {
    }

    /**
     * 후보 대상자의 최근 기관 승인 활동 기록입니다.
     *
     * checklist는
     * 문항 code -> 응답값
     * 형태로 전달합니다.
     *
     * NOT_MET이면 checklist는 null입니다.
     */
    public record RecentRecord(
            int daysAgo,
            String visitResult,
            Map<String, String> checklist
    ) {
    }
}