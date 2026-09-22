package com.dagachi.backend.common.storage;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        /*
         * 실제 실행 환경에서는 application.yml의
         * aws.s3.bucket 값이 @Value로 주입됩니다.
         *
         * 현재 테스트는 Spring Context를 띄우지 않는
         * Mockito 단위 테스트이므로 테스트용 bucket을 직접 주입합니다.
         */
        ReflectionTestUtils.setField(
                s3StorageService,
                "bucket",
                "test-bucket"
        );
    }

    @Test
    @DisplayName("REQ-RPT-05 - 정상 JPEG 이미지는 S3에 업로드하고 Object Key를 반환한다")
    void upload_정상JPEG이미지는_S3에업로드하고_ObjectKey를_반환한다() {
        // given

        /*
         * JPEG magic byte:
         * FF D8 FF
         *
         * Content-Type 문자열만 JPEG라고 지정하는 것이 아니라,
         * 실제 파일 내용도 JPEG 형식으로 인식될 수 있도록
         * 앞 3바이트를 JPEG signature로 구성합니다.
         */
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };

        MockMultipartFile file =
                new MockMultipartFile(
                        "images",
                        "test-image.jpg",
                        "image/jpeg",
                        jpegBytes
                );

        // when
        String key =
                s3StorageService.upload(
                        file,
                        "reports"
                );

        // then
        LocalDate today = LocalDate.now();

        /*
         * 실제 Object Key 규칙:
         *
         * reports/yyyy/MM/UUID.jpg
         */
        assertThat(key)
                .startsWith(
                        "reports/%d/%02d/"
                                .formatted(
                                        today.getYear(),
                                        today.getMonthValue()
                                )
                )
                .endsWith(".jpg");

        /*
         * S3에 전달된 PutObjectRequest까지 캡처하여
         * bucket, key, content-type, size가 올바른지 확인합니다.
         */
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        PutObjectRequest.class
                );

        verify(s3Client)
                .putObject(
                        requestCaptor.capture(),
                        any(RequestBody.class)
                );

        PutObjectRequest putObjectRequest =
                requestCaptor.getValue();

        assertThat(putObjectRequest.bucket())
                .isEqualTo("test-bucket");

        assertThat(putObjectRequest.key())
                .isEqualTo(key);

        assertThat(putObjectRequest.contentType())
                .isEqualTo("image/jpeg");

        assertThat(putObjectRequest.contentLength())
                .isEqualTo((long) jpegBytes.length);
    }

    @Test
    @DisplayName("REQ-RPT-05 - JPEG Content-Type이라도 실제 파일이 JPEG가 아니면 거부한다")
    void upload_JPEGContentType이지만_실제파일이JPEG가아니면_거부한다() {
        // given
        MockMultipartFile file =
                new MockMultipartFile(
                        "images",
                        "fake-image.jpg",
                        "image/jpeg",
                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
                );

        // when & then
        assertThatThrownBy(
                () -> s3StorageService.upload(
                        file,
                        "reports"
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.S3_INVALID_FILE_TYPE);
                });

        /*
         * 실제 파일 형식 검증에서 실패하므로
         * S3 putObject까지 호출되면 안 됩니다.
         */
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("REQ-RPT-05 - 10MB를 초과하는 이미지는 업로드를 거부한다")
    void upload_10MB초과이미지는_업로드를_거부한다() {
        // given
        byte[] oversizedBytes =
                new byte[(10 * 1024 * 1024) + 1];

        /*
         * JPEG magic byte를 넣어 파일 형식 자체는 정상처럼 구성합니다.
         *
         * 이번 테스트 목적은 파일 형식 검증이 아니라
         * 10MB 초과 크기 제한 검증입니다.
         */
        oversizedBytes[0] = (byte) 0xFF;
        oversizedBytes[1] = (byte) 0xD8;
        oversizedBytes[2] = (byte) 0xFF;

        MockMultipartFile file =
                new MockMultipartFile(
                        "images",
                        "too-large.jpg",
                        "image/jpeg",
                        oversizedBytes
                );

        // when & then
        assertThatThrownBy(
                () -> s3StorageService.upload(
                        file,
                        "reports"
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.S3_FILE_TOO_LARGE);
                });

        /*
         * 크기 검증에서 실패하므로
         * 실제 S3 업로드는 수행되면 안 됩니다.
         */
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("REQ-RPT-05 - Content-Type과 실제 이미지 형식이 다르면 업로드를 거부한다")
    void upload_ContentType과_실제이미지형식이_다르면_거부한다() {
        // given

        /*
         * 실제 파일 내용은 PNG magic byte로 구성합니다.
         */
        byte[] pngBytes = new byte[]{
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        /*
         * 하지만 클라이언트가 Content-Type을 image/jpeg로 전달한
         * 불일치 상황을 재현합니다.
         */
        MockMultipartFile file =
                new MockMultipartFile(
                        "images",
                        "mismatch.jpg",
                        "image/jpeg",
                        pngBytes
                );

        // when & then
        assertThatThrownBy(
                () -> s3StorageService.upload(
                        file,
                        "reports"
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.S3_INVALID_FILE_TYPE);
                });

        /*
         * 형식 불일치 검증에서 실패하므로
         * 실제 S3 업로드는 수행되면 안 됩니다.
         */
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("REQ-RPT-05 - 원본 파일명이 255자를 초과하면 업로드를 거부한다")
    void upload_원본파일명이255자를초과하면_업로드를_거부한다() {
        // given
        String longFilename =
                "a".repeat(256);

        byte[] jpegBytes = new byte[]{
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };

        MockMultipartFile file =
                new MockMultipartFile(
                        "images",
                        longFilename,
                        "image/jpeg",
                        jpegBytes
                );

        // when & then
        assertThatThrownBy(
                () -> s3StorageService.upload(
                        file,
                        "reports"
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                });

        /*
         * 파일명 길이 검증에서 실패하므로
         * 실제 S3 업로드는 수행되면 안 됩니다.
         */
        verifyNoInteractions(s3Client);
    }
}