package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;

import java.time.LocalDateTime;

/**
 * 기관 제보 AI 분석 결과를 외부 API로 반환하기 위한 Response DTO입니다.
 *
 * AIAnalysis Entity 자체를 Controller까지 노출하지 않고,
 * 기관 화면에 필요한 값만 명확한 API 계약으로 전달합니다.
 *
 * DB에서는 AI 결과를 resultJson(JSONB) 형태로 유연하게 저장하지만,
 * REPORT_SUMMARY API에서는 Frontend가 사용하기 쉽도록
 * summary를 명시적인 필드로 제공합니다.
 */
public record ReportAiAnalysisResponse(
        Long analysisId,
        AIAnalysisType analysisType,
        AITargetType targetType,
        Long targetId,
        String summary,
        String model,
        LocalDateTime createdAt
) {

    /**
     * 저장된 AIAnalysis Entity와 제보 요약 문자열을
     * API Response DTO로 변환합니다.
     *
     * resultJson 전체를 외부로 그대로 노출하지 않는 이유는,
     * 향후 DUPLICATE_REPORT나 PRIORITY_CANDIDATE처럼
     * 분석 타입마다 JSON 구조가 달라질 수 있기 때문입니다.
     *
     * 각 분석 기능은 자신에게 맞는 명확한 Response DTO를
     * 제공하도록 분리합니다.
     */
    public static ReportAiAnalysisResponse from(
            AIAnalysis analysis,
            String summary
    ) {
        return new ReportAiAnalysisResponse(
                analysis.getId(),
                analysis.getAnalysisType(),
                analysis.getTargetType(),
                analysis.getTargetId(),
                summary,
                analysis.getModelName(),
                analysis.getCreatedAt()
        );
    }
}