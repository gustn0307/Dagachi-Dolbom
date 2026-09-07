package com.dagachi.backend.common.kakao.dto;

import java.math.BigDecimal;

/**
 * 위도/경도 좌표를 전달하기 위한 내부 DTO입니다.
 *
 * Kakao 주소 검색 API에서는
 * x = 경도(longitude),
 * y = 위도(latitude)
 * 로 반환하므로 변환 시 순서를 주의해야 합니다.
 */
public record Coordinate(
        BigDecimal latitude,
        BigDecimal longitude
) {
}
