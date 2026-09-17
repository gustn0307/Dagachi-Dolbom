package com.dagachi.backend.user.report.service;

import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.repository.ReportImageRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.service.ReportEmbeddingService;
import com.dagachi.backend.institution.report.service.ReportTitleGenerationService;
import com.dagachi.backend.user.report.dto.ReportCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ReportService 전체가 아니라, 유지훈님이 이 파일에 추가한 부분(제보 접수 이후
 * AI 제목 생성을 커밋 후 트리거하는 registerTitleGenerationAfterCommit)만 검증한다.
 *
 * 회원/비회원 검증, S3 사진 업로드/보상삭제, Kakao 좌표 변환, embedding 트리거 등
 * 이 파일의 나머지 로직은 원래 담당자의 별도 테스트 범위로 남겨둔다.
 *
 * registerTitleGenerationAfterCommit()은 TransactionSynchronizationManager의
 * afterCommit 콜백으로 등록되므로, 실제 DB 트랜잭션 없이도 이 클래스를 직접
 * init/clearSynchronization()으로 다뤄서 콜백이 실행되는 것처럼 재현한다.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportImageRepository reportImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private KakaoLocalClient kakaoLocalClient;
    @Mock
    private ReportEmbeddingService reportEmbeddingService;
    @Mock
    private ReportTitleGenerationService reportTitleGenerationService;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ReportCreateRequest buildGuestRequest(String content) {
        // address/latitude/longitude는 null로 두어 Kakao 좌표 변환(kakaoLocalClient) 호출 자체를 피한다.
        return new ReportCreateRequest(content, null, null, null, "010-1234-5678");
    }

    @Test
    @DisplayName("createReport - 정상 접수 후 커밋되면 저장된 content(trim 적용)로 AI 제목 생성을 트리거한다")
    void createReport_커밋후_AI제목생성을_트리거한다() {
        ReportCreateRequest request = buildGuestRequest("  긴급 확인이 필요한 상황입니다  ");

        given(reportRepository.save(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 99L);
            return report;
        });

        reportService.createReport(null, request, null);

        // 실제 DB 트랜잭션이 commit된 상황을 재현하기 위해 등록된 afterCommit 콜백을 모두 실행한다.
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        // content는 저장 전에 이미 trim()되어 Report에 들어가므로, 그 trim된 값 그대로 전달돼야 한다.
        verify(reportTitleGenerationService)
                .generateAndSaveTitleAsync(eq(99L), eq("긴급 확인이 필요한 상황입니다"));
    }

    @Test
    @DisplayName("createReport - 활성화된 트랜잭션 동기화가 없으면 AI 제목 생성 트리거 자체가 등록되지 않는다")
    void createReport_트랜잭션동기화가_없으면_제목생성을_등록하지_않는다() {
        // 이 테스트만 setUp에서 켜둔 동기화를 일부러 끈다(활성 트랜잭션이 없는 상황 재현).
        TransactionSynchronizationManager.clearSynchronization();

        ReportCreateRequest request = buildGuestRequest("내용");

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // 트랜잭션 동기화가 없어도 예외 없이 정상 응답해야 한다(로그만 남기고 조용히 넘어감).
        reportService.createReport(null, request, null);

        verify(reportTitleGenerationService, never()).generateAndSaveTitleAsync(any(), any());
    }
}