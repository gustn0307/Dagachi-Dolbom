package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.time.LocalDateTime;

/**
 * 현재 로그인한 기관에 이미 배정된 제보의 목록 응답 DTO입니다.
 *
 * 기존 REPORT-03의 "로그인 담당자의 기관 범위 제보 목록"에 사용합니다.
 * 목록 조회이므로 Reporter Entity나 guestPhone 같은 개인정보는 노출하지 않습니다.
 * content는 원문이며, aiSummary가 있으면 목록 화면에서는
 * content 대신 aiSummary를 우선 표시합니다(Frontend에서 aiSummary ?? content).
 * aiSummary가 null이면 아직 담당자가 상세 페이지에서
 * AI 요약을 한 번도 생성하지 않은 제보입니다.
 */
public record InstitutionReportListItemResponse(
        Long reportId,
        String content,
        String aiSummary,
        String address,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static InstitutionReportListItemResponse from(
            Report report,
            String aiSummary
    ) {
        return new InstitutionReportListItemResponse(
                report.getId(),
                report.getContent(),
                aiSummary,
                report.getAddress(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}