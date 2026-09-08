package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 제보 중복/유사 분석 결과를 기관 담당자에게 반환하는 DTO입니다.
 *
 * AI가 중복 여부를 확정하지 않고,
 * 내용 유사도와 위치/시간 정보를 포함한 후보 목록만 제공합니다.
 */
public record ReportDuplicateAnalysisResponse(

        Long analysisId,

        AIAnalysisType analysisType,

        AITargetType targetType,

        Long targetId,

        List<ReportDuplicateCandidateResponse> candidates,

        String model,

        LocalDateTime createdAt
) {
}