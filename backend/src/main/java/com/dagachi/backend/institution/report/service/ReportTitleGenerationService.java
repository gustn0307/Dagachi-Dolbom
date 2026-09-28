package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiReportTitleResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.enums.RetryTitleScope;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.dto.RetryMissingTitleResponse;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 제보 목록 화면에 표시할 한줄 제목(REPORT_TITLE)을
 * REPORT_SUMMARY(상세 화면 문단 요약)와 완전히 독립적으로 생성합니다.
 *
 * 1) 제보 접수 시점, 비동기 자동 (generateAndSaveTitleAsync)
 * 2) "AI 요약 재생성" 버튼, 동기 일괄 처리 (retryMissingTitles)
 *
 * 두 경로 모두 attemptGenerateTitle을 공유합니다.
 */
@Slf4j
@Service
public class ReportTitleGenerationService {

    private static final int RETRY_BATCH_LIMIT = 100;

    private final ReportRepository reportRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final AiServiceClient aiServiceClient;
    private final UserRepository userRepository;

    public ReportTitleGenerationService(
            ReportRepository reportRepository,
            AIAnalysisRepository aiAnalysisRepository,
            AiServiceClient aiServiceClient,
            UserRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiServiceClient = aiServiceClient;
        this.userRepository = userRepository;
    }

    /**
     * 제보 접수 시점 자동 트리거용. 호출 즉시 반환되며,
     * 실패해도 예외를 던지지 않고 로그만 남깁니다.
     */
    @Async("aiTitleTaskExecutor")
    @Transactional
    public void generateAndSaveTitleAsync(
            Long reportId,
            String content
    ) {
        boolean success = attemptGenerateTitle(reportId, content);

        if (!success) {
            log.warn("제보 AI 제목 생성에 실패했습니다(비동기). reportId={}", reportId);
        }
    }

    /**
     * "AI 요약 재생성" 버튼 전용.
     *
     * scope(내 기관 / 미배정) 전체에서 REPORT_TITLE이 없는 제보만
     * 필터·현재 페이지와 무관하게 오래된 순으로 최대 RETRY_BATCH_LIMIT건 동기 처리합니다.
     */
    @Transactional
    public RetryMissingTitleResponse retryMissingTitles(
            Long userId,
            RetryTitleScope scope
    ) {
        List<Long> targetIds = resolveTargetReportIds(userId, scope);

        boolean hasMore = targetIds.size() > RETRY_BATCH_LIMIT;

        List<Long> batch = hasMore
                ? targetIds.subList(0, RETRY_BATCH_LIMIT)
                : targetIds;

        int succeeded = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long reportId : batch) {
            Report report = reportRepository.findById(reportId).orElse(null);

            if (report == null) {
                continue;
            }

            boolean success = attemptGenerateTitle(reportId, report.getContent());

            if (success) {
                succeeded++;
            } else {
                failedIds.add(reportId);
            }
        }

        return new RetryMissingTitleResponse(
                batch.size(),
                succeeded,
                failedIds.size(),
                failedIds,
                hasMore
        );
    }

    /**
     * 실제 OpenAI 호출과 AIAnalysis 저장을 수행하는 공통 로직입니다.
     * 예외를 던지지 않고 성공 여부만 boolean으로 반환합니다.
     */
    private boolean attemptGenerateTitle(
            Long reportId,
            String content
    ) {
        if (!reportRepository.existsById(reportId)) {
            log.warn("AI 제목 생성 대상 제보가 존재하지 않습니다. reportId={}", reportId);
            return false;
        }

        try {
            AiReportTitleResponse aiResponse =
                    aiServiceClient.generateReportTitle(content);

            ObjectNode resultJson = JsonNodeFactory.instance.objectNode();
            resultJson.put("title", aiResponse.title());

            AIAnalysis analysis = AIAnalysis.create(
                    AIAnalysisType.REPORT_TITLE,
                    AITargetType.REPORT,
                    reportId,
                    resultJson,
                    aiResponse.model()
            );

            aiAnalysisRepository.save(analysis);
            return true;

        } catch (RuntimeException exception) {
            log.warn("제보 AI 제목 생성에 실패했습니다. reportId={}", reportId, exception);
            return false;
        }
    }

    /**
     * scope에 따라 REPORT_TITLE이 없는 제보 ID를 최대 (RETRY_BATCH_LIMIT + 1)건 조회합니다.
     * +1건은 hasMore 여부를 별도 COUNT 쿼리 없이 판단하기 위함입니다.
     */
    private List<Long> resolveTargetReportIds(
            Long userId,
            RetryTitleScope scope
    ) {
        Pageable limit = PageRequest.of(0, RETRY_BATCH_LIMIT + 1);

        return switch (scope) {
            case MY_INSTITUTION -> {
                Institution institution = getInstitutionByUserId(userId);
                yield reportRepository.findMissingTitleReportIdsByInstitution(
                        institution.getId(),
                        limit
                );
            }
            case UNASSIGNED -> {
                userRepository.findByIdAndDeletedFalse(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                yield reportRepository.findMissingTitleReportIdsUnassigned(limit);
            }
        };
    }

    private Institution getInstitutionByUserId(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Institution institution = user.getInstitution();

        if (institution == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return institution;
    }
}