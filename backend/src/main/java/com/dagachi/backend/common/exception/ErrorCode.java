package com.dagachi.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_500",
            "서버 내부 오류가 발생했습니다."
    ),

    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "COMMON_400",
            "입력값이 올바르지 않습니다."
    ),

    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "COMMON_415",
            "지원하지 않는 요청 형식입니다."
    ),

    // User
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_404",
            "사용자를 찾을 수 없습니다."
    ),

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "USER_409",
            "이미 사용 중인 이메일입니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "AUTH_401_INVALID_CREDENTIALS",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    ACCOUNT_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "AUTH_403_SUSPENDED",
            "정지된 계정입니다."
    ),

    ACCOUNT_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "AUTH_403_WITHDRAWN",
            "탈퇴한 계정입니다."
    ),

    // Security
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_401",
            "인증이 필요합니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "AUTH_403",
            "접근 권한이 없습니다."
    ),

    // Report
    REPORT_GUEST_PHONE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "REPORT_400_GUEST_PHONE_REQUIRED",
            "비회원 제보 시 연락처는 필수입니다."
    ),

    REPORT_IMAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "REPORT_400_IMAGE_LIMIT",
            "제보 사진은 최대 3장까지 첨부할 수 있습니다."
    ),

    // Checklist
    CHECKLIST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHECKLIST_404",
            "해당 버전의 체크리스트를 찾을 수 없습니다."
    ),

    // S3
    S3_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "S3_500_UPLOAD",
            "파일 업로드 중 오류가 발생했습니다."
    ),

    S3_DELETE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "S3_500_DELETE",
            "파일 삭제 중 오류가 발생했습니다."
    ),

    S3_URL_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "S3_500_URL",
            "파일 조회 URL 생성 중 오류가 발생했습니다."
    ),

    S3_EMPTY_FILE(
            HttpStatus.BAD_REQUEST,
            "S3_400_EMPTY_FILE",
            "업로드할 파일이 비어 있습니다."
    ),

    S3_INVALID_FILE_TYPE(
            HttpStatus.BAD_REQUEST,
            "S3_400_FILE_TYPE",
            "허용되지 않는 파일 형식입니다."
    ),

    S3_FILE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "S3_400_FILE_SIZE",
            "파일 크기가 허용 범위를 초과했습니다."
    ),

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMON_404",
            "요청한 리소스를 찾을 수 없습니다."
    ),

    ACTIVITY_NOT_RECRUITING(
            HttpStatus.CONFLICT,
        "ACTIVITY_409_NOT_RECRUITING",
                "모집 중인 활동이 아닙니다."
    ),

    // ActivityApplication
    APPLICATION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
        "APPLICATION_409_DUPLICATE",
                "이미 신청한 활동입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

}