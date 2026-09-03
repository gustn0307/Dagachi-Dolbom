package com.dagachi.backend.user.activity.dto;

import java.time.LocalDateTime;

// RECORD-04 서명 업로드 성공 응답 DTO
public record ActivityRecordSignatureResponse(
        boolean signatureUploaded,
        LocalDateTime signedAt
) {
}