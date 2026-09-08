package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.enums.ReportStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 중복/유사 제보 분석에서 기관 담당자에게 보여줄
 * 유사 제보 후보 한 건을 나타냅니다.
 *
 * similarity는 중복 확률이 아니라
 * embedding cosine similarity 기반의 "내용 유사도"입니다.
 *
 * distanceKm은 후보를 제거하는 필터가 아니라
 * 담당자의 최종 판단을 돕기 위한 참고 정보입니다.
 */
public record ReportDuplicateCandidateResponse(

        // 후보 제보 ID
        Long reportId,

        // 원문 전체가 아니라 앞부분 최대 100자
        String contentPreview,

        // 유사도
        Double similarity,

        // 후보 제보와의 거리
        BigDecimal distanceKm,

        // 접수 시점
        LocalDateTime createdAt,

        // 제보 상태
        ReportStatus status,

        // 미배정 제보인지 현제 기관에 이미 배정된 제보인지
        boolean assigned
) {
}