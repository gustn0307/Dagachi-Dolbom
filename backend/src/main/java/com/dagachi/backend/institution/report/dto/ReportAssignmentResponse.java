package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;

/**
 * 기관이 미배정 제보를 자신의 관할로 지정한 결과를 반환하는 DTO입니다.
 *
 * Entity 자체를 외부 API로 노출하지 않고,
 * 관할 지정 완료를 확인하는 데 필요한 최소 정보만 반환합니다.
 */
public record ReportAssignmentResponse(
        Long reportId,
        Long institutionId,
        String institutionName
) {

    /**
     * 기관 배정이 완료된 Report를 API 응답 DTO로 변환합니다.
     */
    public static ReportAssignmentResponse from(
            Report report
    ) {
        return new ReportAssignmentResponse(
                report.getId(),
                report.getInstitution().getId(),
                report.getInstitution().getName()
        );
    }
}