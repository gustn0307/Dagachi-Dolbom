package com.dagachi.backend.institution.report.dto;

public record ReportEmbeddingResult(
        float[] embedding,
        String model
) {
}