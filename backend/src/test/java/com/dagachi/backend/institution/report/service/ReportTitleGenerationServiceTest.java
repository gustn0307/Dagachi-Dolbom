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
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.AIAnalysisRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.dto.RetryMissingTitleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ReportTitleGenerationService 단위 테스트.
 *
 * 1) generateAndSaveTitleAsync: 제보 접수 시점 비동기 자동 생성
 * 2) retryMissingTitles: "AI 요약 재생성" 버튼, 동기 일괄 처리
 * 두 경로가 공유하는 attemptGenerateTitle의 성공/실패 분기를 함께 검증한다.
 *
 * @Async는 Spring AOP 프록시를 통해서만 동작하므로, 순수 Mockito 단위 테스트에서
 * service 메서드를 직접 호출하면 동기적으로 실행된다.
 */
@ExtendWith(MockitoExtension.class)
class ReportTitleGenerationServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AIAnalysisRepository aiAnalysisRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportTitleGenerationService reportTitleGenerationService;

    private static final Long USER_ID = 1L;

    private User buildUser(long id) {
        User user = User.create(
                "user" + id + "@test.com", "encoded-pw", "테스터" + id,
                "닉네임" + id, "010-0000-0000", UserGender.MALE
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Report buildReport(String content) {
        return Report.create(null, null, content, null, null, null);
    }

    // ---------------------------------------------------------------
    // generateAndSaveTitleAsync (제보 접수 시점 자동 생성)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-AI-12 - generateAndSaveTitleAsync - 대상 제보가 없으면 AI를 호출하지 않고 조용히 끝난다")
    void generateAndSaveTitleAsync_제보가_없으면_아무일도_하지_않는다() {
        given(reportRepository.existsById(10L)).willReturn(false);

        assertThatCode(() -> reportTitleGenerationService.generateAndSaveTitleAsync(10L, "내용"))
                .doesNotThrowAnyException();

        verify(aiServiceClient, never()).generateReportTitle(any());
        verify(aiAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-AI-12 - generateAndSaveTitleAsync - 정상 생성되면 REPORT_TITLE 타입의 AIAnalysis를 저장한다")
    void generateAndSaveTitleAsync_성공하면_AIAnalysis를_저장한다() {
        given(reportRepository.existsById(10L)).willReturn(true);
        given(aiServiceClient.generateReportTitle("어르신이 며칠째 안 보인다는 제보"))
                .willReturn(new AiReportTitleResponse("독거노인 안전 확인 요청", "gpt-4o-mini"));

        reportTitleGenerationService.generateAndSaveTitleAsync(10L, "어르신이 며칠째 안 보인다는 제보");

        ArgumentCaptor<AIAnalysis> captor = ArgumentCaptor.forClass(AIAnalysis.class);
        verify(aiAnalysisRepository).save(captor.capture());

        AIAnalysis saved = captor.getValue();
        assertThat(saved.getAnalysisType()).isEqualTo(AIAnalysisType.REPORT_TITLE);
        assertThat(saved.getTargetType()).isEqualTo(AITargetType.REPORT);
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(saved.getResultJson().get("title").asText()).isEqualTo("독거노인 안전 확인 요청");
    }

    @Test
    @DisplayName("REQ-AI-12 - generateAndSaveTitleAsync - AI 호출이 실패해도 예외를 밖으로 던지지 않는다")
    void generateAndSaveTitleAsync_AI호출_실패해도_예외를_던지지_않는다() {
        given(reportRepository.existsById(10L)).willReturn(true);
        given(aiServiceClient.generateReportTitle(any()))
                .willThrow(new CustomException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        assertThatCode(() -> reportTitleGenerationService.generateAndSaveTitleAsync(10L, "내용"))
                .doesNotThrowAnyException();

        verify(aiAnalysisRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // retryMissingTitles (일괄 재생성)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - MY_INSTITUTION scope에서 사용자가 없으면 USER_NOT_FOUND")
    void retryMissingTitles_MY_INSTITUTION_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.MY_INSTITUTION)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - MY_INSTITUTION scope에서 소속 기관이 없으면 FORBIDDEN")
    void retryMissingTitles_MY_INSTITUTION_소속기관이_없으면_예외를_던진다() {
        User user = buildUser(USER_ID); // institution 미설정 -> null
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() ->
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.MY_INSTITUTION)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - MY_INSTITUTION scope는 소속 기관 ID 기준으로 대상을 조회한다")
    void retryMissingTitles_MY_INSTITUTION_소속기관_기준으로_조회한다() {
        User user = buildUser(USER_ID);
        Institution institution = mock(Institution.class);
        given(institution.getId()).willReturn(50L);
        ReflectionTestUtils.setField(user, "institution", institution);

        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findMissingTitleReportIdsByInstitution(eq(50L), any(Pageable.class)))
                .willReturn(List.of());

        RetryMissingTitleResponse response =
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.MY_INSTITUTION);

        assertThat(response.targetCount()).isZero();
        verify(reportRepository).findMissingTitleReportIdsByInstitution(eq(50L), any(Pageable.class));
        verify(reportRepository, never()).findMissingTitleReportIdsUnassigned(any());
    }

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - UNASSIGNED scope에서 사용자가 없으면 USER_NOT_FOUND")
    void retryMissingTitles_UNASSIGNED_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.UNASSIGNED)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - 대상이 없으면 0건 결과를 반환한다")
    void retryMissingTitles_대상이_없으면_0건을_반환한다() {
        User user = buildUser(USER_ID);
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findMissingTitleReportIdsUnassigned(any(Pageable.class)))
                .willReturn(List.of());

        RetryMissingTitleResponse response =
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.UNASSIGNED);

        assertThat(response.targetCount()).isZero();
        assertThat(response.succeededCount()).isZero();
        assertThat(response.failedCount()).isZero();
        assertThat(response.failedReportIds()).isEmpty();
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    @DisplayName("REQ-AI-12 - [현재 동작 기록] retryMissingTitles - 조회 이후 삭제된 제보는 targetCount엔 포함되지만 성공/실패 어디에도 잡히지 않는다")
    void retryMissingTitles_성공과_실패와_유실이_섞이면_개수를_정확히_센다() {
        User user = buildUser(USER_ID);
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findMissingTitleReportIdsUnassigned(any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L));

        // 1번: 정상 성공
        given(reportRepository.findById(1L)).willReturn(Optional.of(buildReport("A 내용")));
        given(reportRepository.existsById(1L)).willReturn(true);
        given(aiServiceClient.generateReportTitle("A 내용"))
                .willReturn(new AiReportTitleResponse("제목A", "gpt-4o-mini"));

        // 2번: 목록 조회 이후 실제로 삭제되어 findById가 비어있음 (attemptGenerateTitle까지 가지 않음)
        given(reportRepository.findById(2L)).willReturn(Optional.empty());

        // 3번: 존재하지만 AI 호출 자체가 실패
        given(reportRepository.findById(3L)).willReturn(Optional.of(buildReport("C 내용")));
        given(reportRepository.existsById(3L)).willReturn(true);
        given(aiServiceClient.generateReportTitle("C 내용"))
                .willThrow(new CustomException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        RetryMissingTitleResponse response =
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.UNASSIGNED);

        assertThat(response.targetCount()).isEqualTo(3);
        assertThat(response.succeededCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.failedReportIds()).containsExactly(3L);
        // 3 != 1(성공) + 1(실패): 2번 건은 유실되어 어느 쪽에도 집계되지 않는다.
        assertThat(response.succeededCount() + response.failedCount())
                .isLessThan(response.targetCount());

        verify(reportRepository, never()).existsById(2L);
    }

    @Test
    @DisplayName("REQ-AI-12 - retryMissingTitles - 대상이 100건을 초과하면 100건만 처리하고 hasMore=true를 반환한다")
    void retryMissingTitles_101건_이상이면_100건만_처리한다() {
        User user = buildUser(USER_ID);
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        List<Long> targetIds = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            targetIds.add(i);
        }
        given(reportRepository.findMissingTitleReportIdsUnassigned(any(Pageable.class)))
                .willReturn(targetIds);

        given(reportRepository.findById(anyLong())).willReturn(Optional.of(buildReport("내용")));
        given(reportRepository.existsById(anyLong())).willReturn(true);
        given(aiServiceClient.generateReportTitle(any()))
                .willReturn(new AiReportTitleResponse("제목", "gpt-4o-mini"));

        RetryMissingTitleResponse response =
                reportTitleGenerationService.retryMissingTitles(USER_ID, RetryTitleScope.UNASSIGNED);

        assertThat(response.targetCount()).isEqualTo(100);
        assertThat(response.succeededCount()).isEqualTo(100);
        assertThat(response.hasMore()).isTrue();

        // 101번째(id=101)는 100건 제한에 걸려 처리 대상에서 제외되어야 한다.
        verify(reportRepository, never()).findById(101L);
    }
}