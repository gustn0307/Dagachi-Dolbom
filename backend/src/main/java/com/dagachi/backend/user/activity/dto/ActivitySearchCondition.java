package com.dagachi.backend.user.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ActivitySearchCondition(
        BigDecimal latitude,
        BigDecimal longitude,
        String region,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<String> ageGroups,
        String gender,
        String sortBy
) {
    /** 안부 확인이 오래된 순 정렬을 나타내는 sortBy 값. */
    private static final String SORT_BY_STALE = "STALE";

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public boolean hasAgeGroups() {
        return ageGroups != null && !ageGroups.isEmpty();
    }

    public boolean hasGender() {
        return gender != null && !gender.isBlank();
    }

    /**
     * 대상자의 최근 안부 확인일이 오래된 순으로 정렬해야 하는지 여부.
     * 좌표가 함께 오면(hasCoordinates() == true) 거리순이 우선한다.
     */
    public boolean isStaleSort() {
        return SORT_BY_STALE.equalsIgnoreCase(sortBy);
    }
}