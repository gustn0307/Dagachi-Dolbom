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

    // 사진, 서명 업로드 메서드
    public String upload(MultipartFile file, String prefix) {
        // S3에 업로드하기 전에 빈 파일, 형식, 크기를 검증한다.
        validateFile(file);

        // S3에 저장할 Object Key 생성
        String key = createObjectKey(file, prefix);

        try {
            // 업로드할 파일의 메타데이터 설정
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            // 실제 파일 데이터를 S3에 업로드
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            // DB에는 전체 URL이 아닌 S3 Object Key를 저장할 수 있도록 반환
            return key;

        } catch (IOException | S3Exception e) {
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED);
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

    private String createObjectKey(MultipartFile file, String prefix) {
        LocalDate now = LocalDate.now();

        String extension = getExtension(file.getOriginalFilename());

        return "%s/%d/%02d/%s.%s".formatted(
                prefix,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                extension
        );
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "bin";
        }

        return originalFilename
                .substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase();
    }

    // 업로드 가능한 이미지인지 공통 검증
    private void validateFile(MultipartFile file) {
        // 파일이 없거나 내용이 비어 있는 경우 업로드하지 않는다.
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.S3_EMPTY_FILE);
        }

        // 현재 MVP에서는 JPEG, PNG 이미지만 허용한다.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.S3_INVALID_FILE_TYPE);
        }

        // 지나치게 큰 파일 업로드를 방지한다.
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.S3_FILE_TOO_LARGE);
        }
    }
}