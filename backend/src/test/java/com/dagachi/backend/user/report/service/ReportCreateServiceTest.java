package com.dagachi.backend.user.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.kakao.dto.Coordinate;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.ReportImage;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportCreateServiceTest {

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

    @Test
    @DisplayName("REQ-RPT-01, REQ-RPT-03 - INSTITUTION 계정은 USER 회원 제보자로 등록할 수 없다")
    void createReport_INSTITUTION은_회원제보자로_등록할수없다() {
        // given
        Long userId = 100L;

        User institutionUser = mock(User.class);

        given(institutionUser.getRole())
                .willReturn(UserRole.INSTITUTION);

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.of(institutionUser));

        ReportCreateRequest request =
                new ReportCreateRequest(
                        "제보 내용",
                        "경기도 평택시 테스트 주소",
                        null,
                        null,
                        null
                );

        // when & then
        assertThatThrownBy(
                () -> reportService.createReport(
                        userId,
                        request,
                        null
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
         * USER가 아닌 인증 계정은 제보 저장 단계까지
         * 진행되어서는 안 됩니다.
         */
        verifyNoInteractions(reportRepository);
        verifyNoInteractions(s3StorageService);
        verifyNoInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("REQ-RPT-01, REQ-RPT-03, REQ-RPT-06 - USER 회원 제보는 reporter를 저장하고 접수 결과를 반환한다")
    void createReport_USER회원제보는_reporter를_저장하고_guestPhone을_제거한다() {
        // given
        Long userId = 1L;

        User user = mock(User.class);

        given(user.getRole())
                .willReturn(UserRole.USER);

        given(user.getStatus())
                .willReturn(UserStatus.ACTIVE);

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.of(user));

        /*
         * 주소를 넣지 않아 이번 테스트에서는
         * Kakao 좌표 변환 로직을 검증하지 않습니다.
         *
         * 이 테스트의 목적은 회원 제보의 reporter / guestPhone 분기입니다.
         */
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "  회원 제보 내용  ",
                        null,
                        null,
                        null,
                        "010-9999-9999"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 10L);
                    return report;
                });

        // when
        var response = reportService.createReport(
                userId,
                request,
                null
        );

        // then
        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);

        verify(reportRepository)
                .save(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();

        /*
         * 인증된 USER 제보이므로 reporter가 저장되어야 합니다.
         */
        assertThat(savedReport.getReporter())
                .isSameAs(user);

        /*
         * 회원 제보에서는 request에 guestPhone이 들어오더라도
         * 서버가 강제로 null 처리해야 합니다.
         */
        assertThat(savedReport.getGuestPhone())
                .isNull();

        assertThat(savedReport.getContent())
                .isEqualTo("회원 제보 내용");

        assertThat(savedReport.getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);

        assertThat(response.reportId())
                .isEqualTo(10L);

        assertThat(response.status())
                .isEqualTo(ReportStatus.SUBMITTED);

        /*
         * 주소가 없으므로 Kakao Local API를 호출하지 않아야 합니다.
         */
        verifyNoInteractions(kakaoLocalClient);

        /*
         * 이미지가 없으므로 S3 업로드도 수행하지 않습니다.
         */
        verifyNoInteractions(s3StorageService);
    }

    @Test
    @DisplayName("REQ-RPT-02, REQ-RPT-03, REQ-RPT-06 - 비회원 제보는 guestPhone을 저장하고 접수 결과를 반환한다")
    void createReport_비회원제보는_reporter없이_guestPhone을_저장한다() {
        // given
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "  비회원 제보 내용  ",
                        null,
                        null,
                        null,
                        " 010-1234-5678 "
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 20L);
                    return report;
                });

        // when
        var response = reportService.createReport(
                null,
                request,
                null
        );

        // then
        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);

        verify(reportRepository)
                .save(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();

        /*
         * 비회원 제보에서는 로그인 사용자가 없으므로
         * reporter가 null이어야 합니다.
         */
        assertThat(savedReport.getReporter())
                .isNull();

        /*
         * 비회원 연락처는 앞뒤 공백을 제거하여 저장합니다.
         */
        assertThat(savedReport.getGuestPhone())
                .isEqualTo("010-1234-5678");

        assertThat(savedReport.getContent())
                .isEqualTo("비회원 제보 내용");

        assertThat(savedReport.getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);

        assertThat(response.reportId())
                .isEqualTo(20L);

        assertThat(response.status())
                .isEqualTo(ReportStatus.SUBMITTED);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(kakaoLocalClient);
        verifyNoInteractions(s3StorageService);
    }


    @Test
    @DisplayName("REQ-RPT-02, REQ-RPT-03 - 비회원 제보에 연락처가 없으면 접수를 거부한다")
    void createReport_비회원제보에_guestPhone이_없으면_거부한다() {
        // given
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "비회원 제보 내용",
                        null,
                        null,
                        null,
                        "   "
                );

        // when & then
        assertThatThrownBy(
                () -> reportService.createReport(
                        null,
                        request,
                        null
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.REPORT_GUEST_PHONE_REQUIRED);
                });

        /*
         * 연락처 검증에서 바로 실패해야 하므로
         * DB 저장이나 외부 서비스 호출까지 진행하면 안 됩니다.
         */
        verifyNoInteractions(userRepository);
        verifyNoInteractions(reportRepository);
        verifyNoInteractions(kakaoLocalClient);
        verifyNoInteractions(s3StorageService);
    }

    @Test
    @DisplayName("REQ-RPT-04 - 입력한 주소를 Kakao 좌표로 변환하여 주소와 좌표를 저장한다")
    void createReport_주소를_Kakao좌표로_변환하여_저장한다() {
        // given
        String inputAddress = "  경기도 평택시 중앙로 1  ";
        String trimmedAddress = "경기도 평택시 중앙로 1";

        BigDecimal latitude = new BigDecimal("36.9921070");
        BigDecimal longitude = new BigDecimal("127.1129450");

        Coordinate coordinate = mock(Coordinate.class);

        given(coordinate.latitude())
                .willReturn(latitude);

        given(coordinate.longitude())
                .willReturn(longitude);

        given(kakaoLocalClient.searchCoordinate(trimmedAddress))
                .willReturn(coordinate);

        ReportCreateRequest request =
                new ReportCreateRequest(
                        "주소 좌표 변환 테스트",
                        inputAddress,
                        null,
                        null,
                        "010-1234-5678"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 30L);
                    return report;
                });

        // when
        var response = reportService.createReport(
                null,
                request,
                null
        );

        // then
        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);

        verify(reportRepository)
                .save(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();

        /*
         * 사용자가 입력한 주소는 앞뒤 공백을 제거하여 저장합니다.
         */
        assertThat(savedReport.getAddress())
                .isEqualTo(trimmedAddress);

        /*
         * 저장되는 좌표는 클라이언트가 직접 입력한 값이 아니라,
         * 해당 주소를 Kakao Local API로 변환한 결과입니다.
         */
        assertThat(savedReport.getLatitude())
                .isEqualByComparingTo(latitude);

        assertThat(savedReport.getLongitude())
                .isEqualByComparingTo(longitude);

        assertThat(response.reportId())
                .isEqualTo(30L);

        verify(kakaoLocalClient)
                .searchCoordinate(trimmedAddress);

        /*
         * 비회원 제보이므로 로그인 사용자 조회는 수행하지 않습니다.
         */
        verifyNoInteractions(userRepository);

        /*
         * 이미지가 없으므로 S3 업로드는 수행하지 않습니다.
         */
        verifyNoInteractions(s3StorageService);
    }

    @Test
    @DisplayName("REQ-RPT-04 - Kakao 좌표 변환이 실패해도 주소는 저장하고 좌표는 null로 접수한다")
    void createReport_Kakao좌표변환실패시_주소는저장하고_좌표는null이다() {
        // given
        String inputAddress = "  경기도 평택시 중앙로 1  ";
        String trimmedAddress = "경기도 평택시 중앙로 1";

        given(kakaoLocalClient.searchCoordinate(trimmedAddress))
                .willThrow(new RuntimeException("Kakao API failure"));

        ReportCreateRequest request =
                new ReportCreateRequest(
                        "Kakao 실패 fallback 테스트",
                        inputAddress,
                        null,
                        null,
                        "010-1234-5678"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 40L);
                    return report;
                });

        // when
        var response = reportService.createReport(
                null,
                request,
                null
        );

        // then
        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);

        verify(reportRepository)
                .save(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();

        assertThat(savedReport.getAddress())
                .isEqualTo(trimmedAddress);

        assertThat(savedReport.getLatitude())
                .isNull();

        assertThat(savedReport.getLongitude())
                .isNull();

        assertThat(savedReport.getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);

        assertThat(response.reportId())
                .isEqualTo(40L);

        verify(kakaoLocalClient)
                .searchCoordinate(trimmedAddress);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(s3StorageService);
    }

    @Test
    @DisplayName("REQ-RPT-05 - 제보 이미지는 최대 3장까지만 허용하고 4장은 거부한다")
    void createReport_이미지4장은_거부한다() {
        // given
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "이미지 개수 제한 테스트",
                        null,
                        null,
                        null,
                        "010-1234-5678"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 50L);
                    return report;
                });

        MockMultipartFile image1 =
                new MockMultipartFile(
                        "images",
                        "1.jpg",
                        "image/jpeg",
                        new byte[]{1}
                );

        MockMultipartFile image2 =
                new MockMultipartFile(
                        "images",
                        "2.jpg",
                        "image/jpeg",
                        new byte[]{1}
                );

        MockMultipartFile image3 =
                new MockMultipartFile(
                        "images",
                        "3.jpg",
                        "image/jpeg",
                        new byte[]{1}
                );

        MockMultipartFile image4 =
                new MockMultipartFile(
                        "images",
                        "4.jpg",
                        "image/jpeg",
                        new byte[]{1}
                );

        // when & then
        assertThatThrownBy(
                () -> reportService.createReport(
                        null,
                        request,
                        List.of(
                                image1,
                                image2,
                                image3,
                                image4
                        )
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED);
                });

        /*
         * 이미지 개수 검증에서 실패하므로
         * 실제 S3 업로드까지 진행하면 안 됩니다.
         */
        verifyNoInteractions(s3StorageService);

        /*
         * Report 자체는 saveImages() 호출 전에 저장되지만,
         * 현재 테스트는 실제 트랜잭션 DB가 아니라 Mock 기반이므로
         * 최종 rollback 여부는 별도 통합 테스트 책임입니다.
         */
        verify(reportRepository)
                .save(any(Report.class));

        verifyNoInteractions(reportImageRepository);
    }

    @Test
    @DisplayName("REQ-RPT-05 - 이미지 1장을 S3에 업로드하고 ReportImage 메타데이터를 저장한다")
    void createReport_이미지1장을_S3업로드하고_ReportImage를_저장한다() {
        // given
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "이미지 업로드 테스트",
                        null,
                        null,
                        null,
                        "010-1234-5678"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 60L);
                    return report;
                });

        MockMultipartFile image =
                new MockMultipartFile(
                        "images",
                        "test-image.jpg",
                        "image/jpeg",
                        new byte[]{1, 2, 3, 4}
                );

        given(s3StorageService.upload(image, "reports"))
                .willReturn("reports/2026/09/test-key.jpg");

        // when
        var response = reportService.createReport(
                null,
                request,
                List.of(image)
        );

        // then
        verify(s3StorageService)
                .upload(image, "reports");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReportImage>> imageListCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(reportImageRepository)
                .saveAllAndFlush(imageListCaptor.capture());

        List<ReportImage> savedImages =
                imageListCaptor.getValue();

        assertThat(savedImages)
                .hasSize(1);

        ReportImage savedImage =
                savedImages.get(0);

        assertThat(savedImage.getS3Key())
                .isEqualTo("reports/2026/09/test-key.jpg");

        assertThat(savedImage.getOriginalFilename())
                .isEqualTo("test-image.jpg");

        assertThat(savedImage.getContentType())
                .isEqualTo("image/jpeg");

        assertThat(savedImage.getFileSize())
                .isEqualTo(4L);

        /*
         * ReportImage가 방금 생성된 Report와 연결되어야 합니다.
         */
        assertThat(savedImage.getReport())
                .isNotNull();

        assertThat(response.reportId())
                .isEqualTo(60L);
    }

    @Test
    @DisplayName("REQ-RPT-05 - 이미지 DB 저장이 실패하면 이미 업로드한 S3 파일을 보상 삭제한다")
    void createReport_ReportImage저장실패시_S3파일을_보상삭제한다() {
        // given
        ReportCreateRequest request =
                new ReportCreateRequest(
                        "S3 보상 삭제 테스트",
                        null,
                        null,
                        null,
                        "010-1234-5678"
                );

        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 70L);
                    return report;
                });

        MockMultipartFile image =
                new MockMultipartFile(
                        "images",
                        "rollback-test.jpg",
                        "image/jpeg",
                        new byte[]{1, 2, 3, 4}
                );

        String uploadedKey =
                "reports/2026/09/rollback-test.jpg";

        given(s3StorageService.upload(image, "reports"))
                .willReturn(uploadedKey);

        /*
         * S3 업로드까지는 성공했지만,
         * ReportImage DB 저장 단계에서 실패한 상황을 재현합니다.
         */
        given(reportImageRepository.saveAllAndFlush(anyList()))
                .willThrow(new RuntimeException("ReportImage DB save failure"));

        // when & then
        assertThatThrownBy(
                () -> reportService.createReport(
                        null,
                        request,
                        List.of(image)
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ReportImage DB save failure");

        /*
         * Mockito 단위 테스트에서는 실제 Spring Transaction rollback이
         * 자동 발생하지 않으므로, 등록된 afterCompletion callback에
         * rollback 상태를 직접 전달합니다.
         */
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {

            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK
            );
        }

        /*
         * DB rollback 시 이번 요청에서 S3에 업로드했던 파일을
         * 보상 삭제해야 합니다.
         */
        verify(s3StorageService)
                .delete(uploadedKey);

        verify(s3StorageService)
                .upload(image, "reports");
    }
}