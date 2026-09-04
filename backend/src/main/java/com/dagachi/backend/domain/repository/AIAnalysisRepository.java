package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AI 분석 결과를 저장하고 조회하기 위한 공통 Repository입니다.
 *
 * AIAnalysis Entity는 REPORT, CARE_RECIPIENT, ACTIVITY 등
 * 여러 도메인에서 공통으로 사용하므로 actor별 Repository를
 * 따로 만들지 않고 domain/repository에서 관리합니다.
 */
public interface AIAnalysisRepository
        extends JpaRepository<AIAnalysis, Long> {

    /**
     * 특정 대상의 특정 AI 분석 타입 중 가장 최근 결과 1건을 조회합니다.
     *
     * 예:
     * targetType = REPORT
     * targetId = 10
     * analysisType = REPORT_SUMMARY
     *
     * 향후 기관 화면에서 최신 AI 결과를 바로 보여줄 때 사용할 수 있습니다.
     */
    Optional<AIAnalysis>
    findTopByTargetTypeAndTargetIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
            AITargetType targetType,
            Long targetId,
            AIAnalysisType analysisType
    );
}