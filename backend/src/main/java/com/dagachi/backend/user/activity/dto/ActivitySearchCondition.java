package com.dagachi.backend.user.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivitySearchCondition(
        BigDecimal latitude,
        BigDecimal longitude,
        String region,
        LocalDate dateFrom,
        LocalDate dateTo
) {
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}