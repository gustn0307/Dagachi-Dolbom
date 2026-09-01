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
        String gender
) {
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public boolean hasAgeGroups() {
        return ageGroups != null && !ageGroups.isEmpty();
    }

    public boolean hasGender() {
        return gender != null && !gender.isBlank();
    }
}