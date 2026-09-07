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
     */
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