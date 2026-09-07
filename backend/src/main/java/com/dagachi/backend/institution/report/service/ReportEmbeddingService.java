package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiReportEmbeddingResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.institution.report.dto.ReportEmbeddingResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

@Service
public class ReportEmbeddingService {

    private final ReportRepository reportRepository;
    private final AiServiceClient aiServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final String embeddingModel;

    public ReportEmbeddingService(
            ReportRepository reportRepository,
            AiServiceClient aiServiceClient,
            PlatformTransactionManager transactionManager,
            @Value("${ai.embedding.model}") String embeddingModel
    ) {
        this.reportRepository = reportRepository;
        this.aiServiceClient = aiServiceClient;
        this.embeddingModel = embeddingModel;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        /*
         * 신규 제보의 afterCommit 시점에서도
         * embedding 저장은 반드시 독립된 새 트랜잭션으로 수행합니다.
         */
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * Report에 embedding이 이미 있으면 그대로 반환하고,
     * 없으면 FastAPI를 통해 생성한 뒤 DB에 저장합니다.
     *
     * 외부 AI 호출 중에는 DB 트랜잭션을 유지하지 않고,
     * 실제 embedding 저장 구간만 짧은 트랜잭션으로 처리합니다.
     */
    public ReportEmbeddingResult ensureEmbedding(
            Long reportId
    ) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * 이미 생성되어 있으면 OpenAI를 다시 호출하지 않습니다.
         */
        if (report.getEmbedding() != null
                && report.getEmbedding().length > 0) {

            return new ReportEmbeddingResult(
                    report.getEmbedding(),
                    embeddingModel
            );
        }

        /*
         * 외부 FastAPI/OpenAI 호출입니다.
         * 이 시점에는 장기 DB 트랜잭션을 유지하지 않습니다.
         */
        AiReportEmbeddingResponse aiResponse =
                aiServiceClient.createReportEmbedding(
                        report.getContent()
                );

        if (!embeddingModel.equals(aiResponse.model())) {
            throw new CustomException(
                    ErrorCode.AI_SERVICE_INVALID_RESPONSE
            );
        }

        float[] embedding = aiResponse.embedding();

        /*
         * AI 호출이 끝난 뒤 embedding 저장에 필요한 짧은
         * DB 트랜잭션만 새로 시작합니다.
         */
        transactionTemplate.executeWithoutResult(status -> {

            Report reportToUpdate =
                    reportRepository.findById(reportId)
                            .orElseThrow(
                                    () -> new CustomException(
                                            ErrorCode.RESOURCE_NOT_FOUND
                                    )
                            );

            /*
             * 호출 사이에 다른 요청이 먼저 embedding을 저장했을 수도 있으므로
             * 아직 비어 있을 때만 갱신합니다.
             */
            if (reportToUpdate.getEmbedding() == null
                    || reportToUpdate.getEmbedding().length == 0) {

                reportToUpdate.updateEmbedding(embedding);
            }
        });

        /*
         * 우리가 생성한 embedding을 호출자에게 반환합니다.
         * 동시에 다른 요청이 먼저 저장했더라도 동일 Report.content와
         * 동일 모델을 사용하는 현재 정책에서는 사용에 문제가 없습니다.
         */
        return new ReportEmbeddingResult(
                embedding,
                aiResponse.model()
        );
    }
}