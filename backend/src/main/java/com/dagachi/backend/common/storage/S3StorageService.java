package com.dagachi.backend.common.storage;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        private final String contentType;
        private final String extension;

        ImageFormat(
                String contentType,
                String extension
        ) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    // 사진, 서명 업로드 메서드
    public String upload(
            MultipartFile file,
            String prefix
    ) {
        /*
         * 빈 파일, 크기, 클라이언트 MIME 타입뿐 아니라
         * 실제 파일의 magic byte까지 확인합니다.
         */
        ImageFormat imageFormat = validateFile(file);

        // 검증된 실제 이미지 형식을 기준으로 Object Key를 생성합니다.
        String key = createObjectKey(
                prefix,
                imageFormat
        );

        try {
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            /*
                             * 클라이언트가 전달한 Content-Type을 그대로 사용하지 않고
                             * 실제 파일 검증 결과를 사용합니다.
                             */
                            .contentType(imageFormat.contentType)
                            .contentLength(file.getSize())
                            .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            return key;

        } catch (IOException | S3Exception e) {
            throw new CustomException(
                    ErrorCode.S3_UPLOAD_FAILED
            );
        }
    }

    // DB에 저장된 Object Key를 이용해 S3 파일을 삭제한다.
    public void delete(String key) {
        // 삭제할 Object Key가 없으면 잘못된 요청으로 처리한다.
        if (key == null || key.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // 지정한 버킷에서 해당 Object Key의 파일을 삭제한다.
            s3Client.deleteObject(deleteObjectRequest);

        } catch (S3Exception e) {
            throw new CustomException(ErrorCode.S3_DELETE_FAILED);
        }
    }

    // DB
    //↓
    // reports/2026/08/UUID.png
    //↓
    // S3StorageService.generatePresignedUrl(key)
    //↓
    // https://dagachi-dolbom-files-dev.s3...
    //     + AWS 임시 서명
    //↓
    // 프론트에서 이미지 표시
    //↓
    // 10분 후 URL 만료
    // 비공개 S3 객체를 일정 시간 동안 조회할 수 있는 임시 URL을 생성한다.
    public String generatePresignedUrl(String key) {
        // 조회할 Object Key가 없으면 잘못된 요청으로 처리한다.
        if (key == null || key.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // Presigned URL의 유효시간을 10분으로 설정한다.
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest)
                    .build();

            // 실제 파일이 public이 아니더라도 이 URL을 가진 사용자는
            // 설정된 유효시간 동안 파일을 조회할 수 있다.
            return s3Presigner
                    .presignGetObject(presignRequest)
                    .url()
                    .toString();

        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.S3_URL_GENERATION_FAILED);
        }
    }

    private String createObjectKey(
            String prefix,
            ImageFormat imageFormat
    ) {
        LocalDate now = LocalDate.now();

        return "%s/%d/%02d/%s.%s".formatted(
                prefix,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                imageFormat.extension
        );
    }

    // 업로드 가능한 이미지인지 공통 검증
    private ImageFormat validateFile(
            MultipartFile file
    ) {
        // 파일이 없거나 내용이 비어 있으면 업로드하지 않습니다.
        if (file == null || file.isEmpty()) {
            throw new CustomException(
                    ErrorCode.S3_EMPTY_FILE
            );
        }

        /*
         * ReportImage.original_filename 컬럼 길이를 초과하지 않도록
         * 업로드 전에 검증합니다.
         */
        String originalFilename =
                file.getOriginalFilename();

        if (originalFilename != null
                && originalFilename.length() > 255) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        /*
         * 1차 검증:
         * multipart 요청에 들어온 Content-Type을 확인합니다.
         *
         * 단, 이 값만 신뢰하지 않고 아래에서 실제 파일 내용도
         * 다시 검증합니다.
         */
        String contentType = file.getContentType();

        if (contentType == null
                || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new CustomException(
                    ErrorCode.S3_INVALID_FILE_TYPE
            );
        }

        // 지나치게 큰 파일 업로드를 방지합니다.
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new CustomException(
                    ErrorCode.S3_FILE_TOO_LARGE
            );
        }

        /*
         * 2차 검증:
         * 실제 파일의 magic byte를 확인해
         * JPEG/PNG 파일인지 검증합니다.
         */
        ImageFormat detectedFormat =
                detectImageFormat(file);

        /*
         * 클라이언트가 전달한 Content-Type과 실제 파일 형식도
         * 서로 일치해야 합니다.
         */
        if (!detectedFormat.contentType.equals(contentType)) {
            throw new CustomException(
                    ErrorCode.S3_INVALID_FILE_TYPE
            );
        }

        return detectedFormat;
    }

    private ImageFormat detectImageFormat(
            MultipartFile file
    ) {
        byte[] header = new byte[8];

        try (InputStream inputStream =
                     file.getInputStream()) {

            int bytesRead = inputStream.read(header);

            /*
             * JPEG
             *
             * FF D8 FF
             */
            if (bytesRead >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {

                return ImageFormat.JPEG;
            }

            /*
             * PNG
             *
             * 89 50 4E 47 0D 0A 1A 0A
             */
            if (bytesRead >= 8
                    && (header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D
                    && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A
                    && (header[7] & 0xFF) == 0x0A) {

                return ImageFormat.PNG;
            }

        } catch (IOException e) {
            throw new CustomException(
                    ErrorCode.S3_INVALID_FILE_TYPE
            );
        }

        throw new CustomException(
                ErrorCode.S3_INVALID_FILE_TYPE
        );
    }
}