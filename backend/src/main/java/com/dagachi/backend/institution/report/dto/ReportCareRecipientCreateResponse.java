package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.institution.recipient.dto.CareRecipientDetailResponse;

/**
 * REPORT-07 신규 돌봄 대상자 생성 및 제보 연결 결과입니다.
 *
 * 생성된 대상자의 상세 정보와
 * 연결된 제보 ID를 함께 반환합니다.
 */
public record ReportCareRecipientCreateResponse(
        Long reportId,
        CareRecipientDetailResponse careRecipient
) {
}