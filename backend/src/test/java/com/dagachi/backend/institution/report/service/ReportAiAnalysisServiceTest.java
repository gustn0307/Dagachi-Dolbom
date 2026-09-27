package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiReportSummaryResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportAiAnalysisServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AIAnalysisRepository aiAnalysisRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportAiAnalysisService reportAiAnalysisService;

    @Test
    @DisplayName("REQ-AI-04, REQ-AI-06 - AI 요약 결과를 REPORT_SUMMARY로 저장하되 제보 업무 상태는 변경하지 않는다")
    void createReportSummary_AI요약결과를_REPORT_SUMMARY로_저장한다() {
        // given
        Long userId = 1L;
        Long institutionId = 10L;
        Long reportId = 100L;

        String content =
                "독거 어르신이 며칠째 보이지 않아 안전 확인이 필요하다는 제보입니다.";

        String summary =
                "독거 어르신의 안전 확인이 필요한 제보";

        String model =
                "gpt-4o-mini";

        Report report =
                Report.create(
                        null,
                        "010-1234-5678",
                        content,
                        "경기도 평택시 테스트 주소",
                        null,
                        null
                );

        ReflectionTestUtils.setField(
                report,
                "id",
                reportId
        );

        Institution institution =
                org.mockito.Mockito.mock(Institution.class);

        given(institution.getId())
                .willReturn(institutionId);

        User user =
                User.create(
                        "institution@test.com",
                        "encoded-password",
                        "기관 담당자",
                        "기관담당자",
                        "010-1111-2222",
                        UserGender.MALE
                );

        ReflectionTestUtils.setField(
                user,
                "institution",
                institution
        );

        given(reportRepository.findById(reportId))
                .willReturn(Optional.of(report));

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.of(user));

        given(reportRepository.existsByIdAndInstitutionId(
                reportId,
                institutionId
        ))
                .willReturn(true);

        given(aiServiceClient.summarizeReport(content))
                .willReturn(
                        new AiReportSummaryResponse(
                                summary,
                                model
                        )
                );

        given(aiAnalysisRepository.save(
                org.mockito.ArgumentMatchers.any(AIAnalysis.class)
        ))
                .willAnswer(invocation -> {
                    AIAnalysis analysis =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            analysis,
                            "id",
                            1000L
                    );

                    ReflectionTestUtils.setField(
                            analysis,
                            "createdAt",
                            LocalDateTime.of(
                                    2026,
                                    9,
                                    28,
                                    3,
                                    0
                            )
                    );

                    return analysis;
                });

        // when
        var response =
                reportAiAnalysisService.createReportSummary(
                        userId,
                        reportId
                );

        // then
        verify(aiServiceClient)
                .summarizeReport(content);

        ArgumentCaptor<AIAnalysis> captor =
                ArgumentCaptor.forClass(AIAnalysis.class);

        verify(aiAnalysisRepository)
                .save(captor.capture());

        AIAnalysis savedAnalysis =
                captor.getValue();

        assertThat(savedAnalysis.getAnalysisType())
                .isEqualTo(AIAnalysisType.REPORT_SUMMARY);

        assertThat(savedAnalysis.getTargetType())
                .isEqualTo(AITargetType.REPORT);

        assertThat(savedAnalysis.getTargetId())
                .isEqualTo(reportId);

        assertThat(savedAnalysis.getModelName())
                .isEqualTo(model);

        assertThat(savedAnalysis.getResultJson())
                .isNotNull();

        assertThat(savedAnalysis.getResultJson().get("summary").asText())
                .isEqualTo(summary);

        assertThat(response.analysisId())
                .isEqualTo(1000L);

        assertThat(response.analysisType())
                .isEqualTo(AIAnalysisType.REPORT_SUMMARY);

        assertThat(response.targetType())
                .isEqualTo(AITargetType.REPORT);

        assertThat(response.targetId())
                .isEqualTo(reportId);

        assertThat(response.summary())
                .isEqualTo(summary);

        assertThat(response.model())
                .isEqualTo(model);

        /*
         * REQ-AI-06:
         * AI 분석은 참고 결과만 생성하며,
         * 제보의 실제 업무 상태를 자동으로 변경해서는 안 됩니다.
         */
        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);

        /*
         * AI 요약 생성 과정에서는 Report Entity 자체를
         * 다시 저장하거나 업무 상태를 변경하지 않습니다.
         */
        verify(reportRepository, never())
                .save(any(Report.class));
    }

    @Test
    @DisplayName("REQ-CMN-02, REQ-AUTH-09 - 다른 기관에 배정된 제보는 AI 분석에 접근할 수 없다")
    void createReportSummary_다른기관_제보는_접근을_거부한다() {
        // given
        Long userId = 1L;
        Long institutionAId = 10L;
        Long otherInstitutionReportId = 200L;

        Report report =
                Report.create(
                        null,
                        "010-1234-5678",
                        "다른 기관에 배정된 제보 내용",
                        "경기도 평택시 테스트 주소",
                        null,
                        null
                );

        ReflectionTestUtils.setField(
                report,
                "id",
                otherInstitutionReportId
        );

        Institution institutionA =
                org.mockito.Mockito.mock(Institution.class);

        given(institutionA.getId())
                .willReturn(institutionAId);

        User user =
                User.create(
                        "institution-a@test.com",
                        "encoded-password",
                        "기관 A 담당자",
                        "기관A담당자",
                        "010-1111-2222",
                        UserGender.MALE
                );

        ReflectionTestUtils.setField(
                user,
                "institution",
                institutionA
        );

        given(reportRepository.findById(otherInstitutionReportId))
                .willReturn(Optional.of(report));

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.of(user));

        /*
         * 제보는 존재하지만 현재 로그인 사용자의 기관 A 소유가 아니다.
         */
        given(reportRepository.existsByIdAndInstitutionId(
                otherInstitutionReportId,
                institutionAId
        ))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                reportAiAnalysisService.createReportSummary(
                        userId,
                        otherInstitutionReportId
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN);
                });

        /*
         * 기관 소유권 검증에서 차단되므로
         * 다른 기관 제보 원문이 AI Service로 전달되어서는 안 된다.
         */
        verifyNoInteractions(aiServiceClient);

        /*
         * 접근이 거부된 요청은 AI 분석 결과도 저장하면 안 된다.
         */
        verifyNoInteractions(aiAnalysisRepository);
    }
}