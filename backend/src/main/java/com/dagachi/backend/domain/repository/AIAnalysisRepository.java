package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /**
     * 여러 targetId의 AIAnalysis를 한 번에 조회합니다.
     *
     * 목록 화면(REPORT-03, 미배정 제보 목록)에서 각 Report마다
     * 최신 REPORT_SUMMARY를 개별 쿼리로 조회하면 N+1이 발생하므로,
     * 페이지에 보이는 reportId를 모아 한 번에 조회한 뒤
     * Service에서 targetId별 최신 1건만 골라 사용합니다.
     *
     * targetId ASC, createdAt DESC, id DESC로 정렬하면
     * 같은 targetId의 결과가 모이고 그 안에서 최신 것이 먼저 옵니다.
     */
    List<AIAnalysis>
    findByTargetTypeAndAnalysisTypeAndTargetIdInOrderByTargetIdAscCreatedAtDescIdDesc(
            AITargetType targetType,
            AIAnalysisType analysisType,
            List<Long> targetIds
    );
}