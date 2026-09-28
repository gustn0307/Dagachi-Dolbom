package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.AIAnalysis;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.ReportImageRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.dto.InstitutionReportListItemResponse;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * InstitutionReportService 전체가 아니라, 유지훈님이 이 파일에서 바꾼 부분
 * (목록 화면의 AI 요약 소스를 REPORT_SUMMARY에서 REPORT_TITLE로 변경한 findLatestReportSummaries/
 * extractSummaryOrNull)만 getInstitutionReports()를 통해 좁게 검증한다.
 *
 * 제보 배정/상태변경/대상자 연결 등 이 파일의 나머지 로직은
 * 원래 담당자(기관 도메인)의 별도 테스트 범위로 남겨둔다.
 */
@ExtendWith(MockitoExtension.class)
class InstitutionReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KakaoLocalClient kakaoLocalClient;
    @Mock
    private ReportImageRepository reportImageRepository;
    @Mock
    private AIAnalysisRepository aiAnalysisRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private CareRecipientRepository careRecipientRepository;

    @InjectMocks
    private InstitutionReportService institutionReportService;

    private static final Long USER_ID = 1L;
    private static final Long INSTITUTION_ID = 10L;
    private static final Long REPORT_ID = 100L;

    private User buildInstitutionUser() {
        User user = User.create(
                "institution@test.com", "encoded-pw", "담당자",
                "닉네임", "010-0000-0000", UserGender.MALE
        );
        Institution institution = mock(Institution.class);
        given(institution.getId()).willReturn(INSTITUTION_ID);
        ReflectionTestUtils.setField(user, "institution", institution);
        return user;
    }

    private Report buildReport() {
        Report report = Report.create(null, null, "제보 원문 내용", "서울시 강남구", null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private AIAnalysis buildTitleAnalysis(Long reportId, String title) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", title);
        return AIAnalysis.create(AIAnalysisType.REPORT_TITLE, AITargetType.REPORT, reportId, json, "gpt-4o-mini");
    }

    @Test
    @DisplayName("REQ-RPT-08, REQ-AI-12 - getInstitutionReports - REPORT_TITLE 분석 결과를 조회해서 aiSummary로 노출한다 (REPORT_SUMMARY 아님)")
    void getInstitutionReports_REPORT_TITLE을_조회해서_aiSummary로_노출한다() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = buildInstitutionUser();
        Report report = buildReport();

        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(report), pageable, 1));
        given(aiAnalysisRepository.findByTargetTypeAndAnalysisTypeAndTargetIdInOrderByTargetIdAscCreatedAtDescIdDesc(
                eq(AITargetType.REPORT), eq(AIAnalysisType.REPORT_TITLE), anyList()
        )).willReturn(List.of(buildTitleAnalysis(REPORT_ID, "독거노인 안전 확인 요청")));

        PageResponse<InstitutionReportListItemResponse> response =
                institutionReportService.getInstitutionReports(USER_ID, null, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).aiSummary()).isEqualTo("독거노인 안전 확인 요청");
        assertThat(response.content().get(0).content()).isEqualTo("제보 원문 내용"); // 원문 자체는 그대로 유지

        // REPORT_SUMMARY가 아니라 REPORT_TITLE로 조회했는지 명시적으로 확인한다.
        verify(aiAnalysisRepository).findByTargetTypeAndAnalysisTypeAndTargetIdInOrderByTargetIdAscCreatedAtDescIdDesc(
                eq(AITargetType.REPORT), eq(AIAnalysisType.REPORT_TITLE), anyList()
        );
    }

    @Test
    @DisplayName("REQ-RPT-08, REQ-AI-14 - getInstitutionReports - 저장된 AI 제목이 없으면 aiSummary는 null이다")
    void getInstitutionReports_AI제목이_없으면_aiSummary는_null이다() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = buildInstitutionUser();
        Report report = buildReport();

        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(report), pageable, 1));
        given(aiAnalysisRepository.findByTargetTypeAndAnalysisTypeAndTargetIdInOrderByTargetIdAscCreatedAtDescIdDesc(
                eq(AITargetType.REPORT), eq(AIAnalysisType.REPORT_TITLE), anyList()
        )).willReturn(List.of());

        PageResponse<InstitutionReportListItemResponse> response =
                institutionReportService.getInstitutionReports(USER_ID, null, null, null, pageable);

        assertThat(response.content().get(0).aiSummary()).isNull();
    }

    @Test
    @DisplayName("REQ-RPT-08, REQ-AUTH-09 - getInstitutionReports - 소속 기관이 없는 사용자는 FORBIDDEN")
    void getInstitutionReports_소속기관이_없으면_예외를_던진다() {
        User user = User.create(
                "no-institution@test.com", "pw", "이름", "닉네임", "010-0000-0000", UserGender.MALE
        ); // institution 미설정
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() ->
                institutionReportService.getInstitutionReports(USER_ID, null, null, null, PageRequest.of(0, 20))
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}