package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

/**
 * REPORT-05 제보 상태 변경 요청입니다.
 *
 * reason은 현재 저장할 도메인 필드/이력 구조가 확정되지 않았으므로
 * MVP V1에서는 status만 받습니다.
 */
public record ReportStatusUpdateRequest(

        @NotNull
        ReportStatus status

) {
}