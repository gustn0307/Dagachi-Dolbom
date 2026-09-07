package com.dagachi.backend.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ReportSimilarityProjection {

    Long getReportId();

    String getContent();

    BigDecimal getLatitude();

    BigDecimal getLongitude();

    String getStatus();

    Long getInstitutionId();

    LocalDateTime getCreatedAt();

    Double getSimilarity();
}