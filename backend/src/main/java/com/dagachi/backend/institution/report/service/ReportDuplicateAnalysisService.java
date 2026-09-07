package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.util.GeoUtils;
import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.ReportSimilarityProjection;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.dto.ReportDuplicateAnalysisResponse;
import com.dagachi.backend.institution.report.dto.ReportDuplicateCandidateResponse;
import com.dagachi.backend.institution.report.dto.ReportEmbeddingResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReportDuplicateAnalysisService {

    private static final int SEARCH_DAYS = 30;
    private static final int TOP_K = 5;

    /*
     * 실제 테스트에서 의미가 비슷한 제보 2건의 cosine similarity가
     * 약 0.705로 확인되어 MVP 초기 기준을 0.70으로 설정합니다.
     *
     * 이 값은 "중복 확률"이 아니라 후보 검색을 위한 내용 유사도 기준입니다.
     */
    private static final double SIMILARITY_THRESHOLD = 0.70;

    private static final int CONTENT_PREVIEW_LENGTH = 100;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final ReportEmbeddingService reportEmbeddingService;

    public ReportDuplicateAnalysisService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            AIAnalysisRepository aiAnalysisRepository,
            ReportEmbeddingService reportEmbeddingService
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.reportEmbeddingService = reportEmbeddingService;
    }

    /**
     * 현재 로그인 기관에 배정된 제보를 기준으로
     * 최근 30일의 미배정 제보 + 현재 기관 제보에서
     * 내용이 유사한 후보를 최대 5건 조회합니다.
     *
     * AI가 중복 여부를 확정하지 않고,
     * 내용 유사도와 위치/접수 시점 등의 참고 정보만 제공합니다.
     */
    public ReportDuplicateAnalysisResponse createDuplicateAnalysis(
            Long userId,
            Long reportId
    ) {

        Report targetReport = reportRepository.findById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        Institution institution = user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 중복 분석 대상 자체는 기존 AI 요약과 동일하게
         * 현재 로그인 기관에 배정된 제보만 허용합니다.
         */
        boolean belongsToInstitution =
                reportRepository.existsByIdAndInstitutionId(
                        reportId,
                        institution.getId()
                );

        if (!belongsToInstitution) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 현재 분석 대상 제보의 embedding은 반드시 필요합니다.
         *
         * 없으면 생성하며, 이 과정이 실패하면
         * 정상적인 similarity 계산이 불가능하므로
         * AI 오류를 그대로 호출자에게 전달합니다.
         */
        ReportEmbeddingResult targetEmbeddingResult =
                reportEmbeddingService.ensureEmbedding(
                        reportId
                );

        LocalDateTime fromDateTime =
                LocalDateTime.now()
                        .minusDays(SEARCH_DAYS);

        /*
         * 과거 데이터 등 embedding이 없는 후보들을 먼저 보완합니다.
         */
        List<Long> missingEmbeddingIds =
                reportRepository.findMissingEmbeddingCandidateIds(
                        reportId,
                        institution.getId(),
                        fromDateTime
                );

        for (Long candidateId : missingEmbeddingIds) {

            try {
                reportEmbeddingService.ensureEmbedding(
                        candidateId
                );

            } catch (RuntimeException exception) {

                /*
                 * 후보 한 건의 embedding 실패 때문에
                 * 전체 중복 분석을 실패시키지 않습니다.
                 *
                 * 해당 후보는 이후 embedding IS NOT NULL 조건에서
                 * 자연스럽게 제외됩니다.
                 */
                log.warn(
                        "중복 제보 후보 embedding 보완에 실패했습니다. reportId={}, candidateId={}",
                        reportId,
                        candidateId,
                        exception
                );
            }
        }

        List<ReportSimilarityProjection> similarReports =
                reportRepository.findSimilarReports(
                        reportId,
                        institution.getId(),
                        fromDateTime,
                        SIMILARITY_THRESHOLD,
                        TOP_K
                );

        List<ReportDuplicateCandidateResponse> candidates =
                similarReports.stream()
                        .map(candidate ->
                                toCandidateResponse(
                                        targetReport,
                                        candidate
                                )
                        )
                        .toList();

        ObjectNode resultJson =
                createResultJson(candidates);

        AIAnalysis analysis = AIAnalysis.create(
                AIAnalysisType.DUPLICATE_REPORT,
                AITargetType.REPORT,
                reportId,
                resultJson,
                targetEmbeddingResult.model()
        );

        AIAnalysis savedAnalysis =
                aiAnalysisRepository.save(analysis);

        return new ReportDuplicateAnalysisResponse(
                savedAnalysis.getId(),
                savedAnalysis.getAnalysisType(),
                savedAnalysis.getTargetType(),
                savedAnalysis.getTargetId(),
                candidates,
                savedAnalysis.getModelName(),
                savedAnalysis.getCreatedAt()
        );
    }

    /**
     * 현재 로그인 기관에 배정된 제보의
     * 최신 중복/유사 제보 분석 결과를 조회합니다.
     *
     * AI를 다시 실행하지 않고 ai_analyses에 저장된
     * 최신 DUPLICATE_REPORT 결과만 반환합니다.
     */
    public ReportDuplicateAnalysisResponse getLatestDuplicateAnalysis(
            Long userId,
            Long reportId
    ) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        Institution institution = user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        boolean belongsToInstitution =
                reportRepository.existsByIdAndInstitutionId(
                        report.getId(),
                        institution.getId()
                );

        if (!belongsToInstitution) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        AIAnalysis analysis =
                aiAnalysisRepository
                        .findTopByTargetTypeAndTargetIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
                                AITargetType.REPORT,
                                reportId,
                                AIAnalysisType.DUPLICATE_REPORT
                        )
                        .orElseThrow(
                                () -> new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
                        );

        return toAnalysisResponse(
                analysis
        );
    }

    private ReportDuplicateCandidateResponse toCandidateResponse(
            Report targetReport,
            ReportSimilarityProjection candidate
    ) {

        BigDecimal distanceKm =
                GeoUtils.calculateDistanceKm(
                        targetReport.getLatitude(),
                        targetReport.getLongitude(),
                        candidate.getLatitude(),
                        candidate.getLongitude()
                );

        ReportStatus status =
                ReportStatus.valueOf(
                        candidate.getStatus()
                );

        boolean assigned =
                candidate.getInstitutionId() != null;

        return new ReportDuplicateCandidateResponse(
                candidate.getReportId(),
                createContentPreview(
                        candidate.getContent()
                ),
                candidate.getSimilarity(),
                distanceKm,
                candidate.getCreatedAt(),
                status,
                assigned
        );
    }

    private ObjectNode createResultJson(
            List<ReportDuplicateCandidateResponse> candidates
    ) {

        ObjectNode resultJson =
                JsonNodeFactory.instance.objectNode();

        ArrayNode candidateArray =
                resultJson.putArray("candidates");

        for (ReportDuplicateCandidateResponse candidate : candidates) {

            ObjectNode candidateNode =
                    candidateArray.addObject();

            candidateNode.put(
                    "reportId",
                    candidate.reportId()
            );

            candidateNode.put(
                    "contentPreview",
                    candidate.contentPreview()
            );

            candidateNode.put(
                    "similarity",
                    candidate.similarity()
            );

            if (candidate.distanceKm() == null) {
                candidateNode.putNull("distanceKm");
            } else {
                candidateNode.put(
                        "distanceKm",
                        candidate.distanceKm()
                );
            }

            candidateNode.put(
                    "createdAt",
                    candidate.createdAt().toString()
            );

            candidateNode.put(
                    "status",
                    candidate.status().name()
            );

            candidateNode.put(
                    "assigned",
                    candidate.assigned()
            );
        }

        return resultJson;
    }

    private String createContentPreview(
            String content
    ) {

        if (content == null
                || content.length() <= CONTENT_PREVIEW_LENGTH) {
            return content;
        }

        return content.substring(
                0,
                CONTENT_PREVIEW_LENGTH
        ) + "...";
    }

    private ReportDuplicateAnalysisResponse toAnalysisResponse(
            AIAnalysis analysis
    ) {

        if (analysis.getResultJson() == null
                || !analysis.getResultJson().has("candidates")
                || !analysis.getResultJson()
                .get("candidates")
                .isArray()) {

            throw new CustomException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        List<ReportDuplicateCandidateResponse> candidates =
                new ArrayList<>();

        for (var candidateNode :
                analysis.getResultJson().get("candidates")) {

            Long candidateReportId =
                    candidateNode.get("reportId").asLong();

            String contentPreview =
                    candidateNode.get("contentPreview").asText();

            Double similarity =
                    candidateNode.get("similarity").asDouble();

            BigDecimal distanceKm =
                    candidateNode.hasNonNull("distanceKm")
                            ? candidateNode.get("distanceKm").decimalValue()
                            : null;

            LocalDateTime createdAt =
                    LocalDateTime.parse(
                            candidateNode.get("createdAt").asText()
                    );

            ReportStatus status =
                    ReportStatus.valueOf(
                            candidateNode.get("status").asText()
                    );

            boolean assigned =
                    candidateNode.get("assigned").asBoolean();

            candidates.add(
                    new ReportDuplicateCandidateResponse(
                            candidateReportId,
                            contentPreview,
                            similarity,
                            distanceKm,
                            createdAt,
                            status,
                            assigned
                    )
            );
        }

        return new ReportDuplicateAnalysisResponse(
                analysis.getId(),
                analysis.getAnalysisType(),
                analysis.getTargetType(),
                analysis.getTargetId(),
                candidates,
                analysis.getModelName(),
                analysis.getCreatedAt()
        );
    }
}