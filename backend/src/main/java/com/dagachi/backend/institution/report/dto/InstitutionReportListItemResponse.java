package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.time.LocalDateTime;

/**
 * 현재 로그인한 기관에 이미 배정된 제보의 목록 응답 DTO입니다.
 *
 * 기존 REPORT-03의 "로그인 담당자의 기관 범위 제보 목록"에 사용합니다.
 * 목록 조회이므로 Reporter Entity나 guestPhone 같은 개인정보는 노출하지 않습니다.
 */
public record InstitutionReportListItemResponse(
        Long reportId,
        String content,
        String address,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static InstitutionReportListItemResponse from(
            Report report
    ) {
        return new InstitutionReportListItemResponse(
                report.getId(),
                report.getContent(),
                report.getAddress(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}