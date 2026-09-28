package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiReportEmbeddingResponse;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportEmbeddingServiceTest {

    private static final String EMBEDDING_MODEL = "test-embedding-model";

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private ReportEmbeddingService reportEmbeddingService;

    @BeforeEach
    void setUp() {
        reportEmbeddingService = new ReportEmbeddingService(
                reportRepository,
                aiServiceClient,
                transactionManager,
                EMBEDDING_MODEL
        );
    }

    @Test
    @DisplayName("REQ-AI-02 - embedding이 없으면 AI로 생성한 embedding을 Report에 저장한다")
    void ensureEmbedding_embedding이_없으면_AI결과를_Report에_저장한다() {
        // given
        Long reportId = 10L;
        String content = "독거 어르신 안전 확인이 필요합니다.";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};

        Report report = mock(Report.class);

        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willReturn(transactionStatus);

        given(report.getEmbedding()).willReturn(null);
        given(report.getContent()).willReturn(content);

        /*
         * ensureEmbedding()은
         * 1) AI 호출 전 조회
         * 2) 저장 트랜잭션 안에서 재조회
         * 두 번 Report를 조회한다.
         */
        given(reportRepository.findById(reportId))
                .willReturn(Optional.of(report));

        given(aiServiceClient.createReportEmbedding(content))
                .willReturn(
                        new AiReportEmbeddingResponse(
                                embedding,
                                EMBEDDING_MODEL
                        )
                );

        // when
        var result =
                reportEmbeddingService.ensureEmbedding(reportId);

        // then
        verify(aiServiceClient)
                .createReportEmbedding(content);

        verify(report)
                .updateEmbedding(embedding);

        verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));

        verify(transactionManager)
                .commit(transactionStatus);

        assertThat(result.embedding())
                .containsExactly(embedding);

        assertThat(result.model())
                .isEqualTo(EMBEDDING_MODEL);
    }

    @Test
    @DisplayName("REQ-AI-02 - embedding이 이미 있으면 AI를 다시 호출하지 않고 기존 값을 반환한다")
    void ensureEmbedding_embedding이_이미있으면_AI를_다시호출하지_않는다() {
        // given
        Long reportId = 20L;
        float[] existingEmbedding =
                new float[]{0.4f, 0.5f, 0.6f};

        Report report = mock(Report.class);

        given(report.getEmbedding())
                .willReturn(existingEmbedding);

        given(reportRepository.findById(reportId))
                .willReturn(Optional.of(report));

        // when
        var result =
                reportEmbeddingService.ensureEmbedding(reportId);

        // then
        verifyNoInteractions(aiServiceClient);

        verify(transactionManager, never())
                .getTransaction(any(TransactionDefinition.class));

        assertThat(result.embedding())
                .containsExactly(existingEmbedding);

        assertThat(result.model())
                .isEqualTo(EMBEDDING_MODEL);
    }
}