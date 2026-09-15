package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 아직 어떤 기관에도 배정되지 않은 제보의 목록 응답 DTO입니다.
 *
 * 미배정 상태에서는 여러 기관이 목록을 볼 수 있으므로
 * 정확한 주소 대신 표시용 지역 정보만 내려주고,
 * guestPhone이나 reporter 개인정보는 포함하지 않습니다.
 *
 * distanceKm은 로그인 기관의 주소를 Kakao Local API로 좌표 변환한 뒤
 * Report 좌표와 GeoUtils로 계산한 거리입니다.
 */
public record UnassignedReportListItemResponse(
        Long reportId,
        String contentPreview,
        String aiSummary,
        String region,
        ReportStatus status,
        BigDecimal distanceKm,
        LocalDateTime createdAt
) {

    private static final int CONTENT_PREVIEW_LENGTH = 100;

    public static UnassignedReportListItemResponse from(
            Report report,
            BigDecimal distanceKm
    ) {
        return new UnassignedReportListItemResponse(
                report.getId(),
                createContentPreview(report.getContent()),
                null, // 정렬/페이징 이후 Service에서 채워 넣는다.
                AddressUtils.extractRegion(report.getAddress()),
                report.getStatus(),
                distanceKm,
                report.getCreatedAt()
        );
    }

    /**
     * 미배정 제보 목록에서 원문 전체를 노출하지 않도록
     * 앞부분만 미리보기 형태로 제공합니다.
     *
     * 상세 원문과 정확한 위치는 기관 배정 이후
     * REPORT-04의 기관 범위 검증을 거쳐 제공하는 방향입니다.
     * 거리 계산·정렬·페이징이 모두 끝난 뒤,
     * 최종 페이지에 포함된 항목에만 AI 요약을 채워 넣기 위한 wither.
     */
    public UnassignedReportListItemResponse withAiSummary(
            String aiSummary
    ) {
        return new UnassignedReportListItemResponse(
                reportId,
                contentPreview,
                aiSummary,
                region,
                status,
                distanceKm,
                createdAt
        );
    }

    private static String createContentPreview(
            String content
    ) {
        if (content == null
                || content.length() <= CONTENT_PREVIEW_LENGTH) {
            return content;
        }

        return content.substring(
                0,
                CONTENT_PREVIEW_LENGTH
        ) + "...";
    }
}