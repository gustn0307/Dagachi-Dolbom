package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiReportSummaryResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.institution.report.dto.ReportAiAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class ReportAiAnalysisService {

    private final ReportRepository reportRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    public ReportAiAnalysisService(
            ReportRepository reportRepository,
            AIAnalysisRepository aiAnalysisRepository,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 특정 제보의 원문을 FastAPI에 전달하여 AI 요약을 생성하고,
     * 생성된 결과를 AIAnalysis에 저장한 뒤 API Response DTO로 반환합니다.
     *
     * 처리 흐름:
     *
     * reportId
     * -> Report 조회
     * -> Report.content 추출
     * -> AiServiceClient
     * -> FastAPI
     * -> OpenAI(gpt-4o-mini)
     * -> summary + model
     * -> resultJson 생성
     * -> AIAnalysis 저장
     * -> ReportAiAnalysisResponse 변환
     *
     * Service 밖으로 JPA Entity를 직접 반환하지 않고
     * API에 필요한 값만 담은 DTO를 반환합니다.
     */
    public ReportAiAnalysisResponse createReportSummary(
            Long reportId
    ) {

        /*
         * AI에 사용자가 임의로 작성한 요청 데이터를 바로 보내지 않고,
         * Spring Boot가 DB에서 실제 Report를 먼저 조회합니다.
         */
        Report report = reportRepository.findById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * REPORT_SUMMARY V1에서는 개인정보 노출을 최소화하기 위해
         * Report.content만 FastAPI/OpenAI로 전달합니다.
         *
         * guestPhone, reporter 개인정보, 주소/좌표, 이미지 등은
         * 현재 AI 입력 범위에서 제외합니다.
         */
        AiReportSummaryResponse aiResponse =
                aiServiceClient.summarizeReport(
                        report.getContent()
                );

        /*
         * AIAnalysis.resultJson은 여러 AI 분석 타입이 공통으로 사용하는
         * JSONB 컬럼입니다.
         *
         * REPORT_SUMMARY의 현재 저장 구조:
         *
         * {
         *   "summary": "AI 요약 결과"
         * }
         *
         * 사용 모델명은 AIAnalysis.modelName 컬럼에 별도로 저장하므로
         * resultJson에 중복해서 넣지 않습니다.
         */
        ObjectNode resultJson =
                objectMapper.createObjectNode();

        resultJson.put(
                "summary",
                aiResponse.summary()
        );

        AIAnalysis analysis = AIAnalysis.create(
                AIAnalysisType.REPORT_SUMMARY,
                AITargetType.REPORT,
                report.getId(),
                resultJson,
                aiResponse.model()
        );

        /*
         * 현재 DB 구조는 같은 제보에 여러 AI 분석 결과를 저장할 수 있습니다.
         *
         * 따라서 사용자가 AI 분석을 다시 실행하면 새로운 행이 생성됩니다.
         * 최신 1건만 표시할지 전체 이력을 보여줄지는
         * AI-01 조회 API 구현 시 최종 정책을 적용합니다.
         */
        AIAnalysis savedAnalysis =
                aiAnalysisRepository.save(analysis);

        /*
         * JPA Entity를 Controller에 그대로 넘기지 않고
         * 외부 API 전용 DTO로 변환해서 반환합니다.
         */
        return ReportAiAnalysisResponse.from(
                savedAnalysis,
                aiResponse.summary()
        );
    }
}