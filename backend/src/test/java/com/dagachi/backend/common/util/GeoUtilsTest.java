package com.dagachi.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeoUtils(Haversine 거리 계산) 단위 테스트.
 */
class GeoUtilsTest {

    @Test
    @DisplayName("calculateDistanceKm - 좌표 중 하나라도 null이면 null을 반환한다")
    void calculateDistanceKm_좌표가_하나라도_없으면_null을_반환한다() {
        BigDecimal lat = new BigDecimal("37.5665");
        BigDecimal lng = new BigDecimal("126.9780");

        assertThat(GeoUtils.calculateDistanceKm(null, lng, lat, lng)).isNull();
        assertThat(GeoUtils.calculateDistanceKm(lat, null, lat, lng)).isNull();
        assertThat(GeoUtils.calculateDistanceKm(lat, lng, null, lng)).isNull();
        assertThat(GeoUtils.calculateDistanceKm(lat, lng, lat, null)).isNull();
    }

    @Test
    @DisplayName("calculateDistanceKm - 동일한 좌표면 거리는 0.0km다")
    void calculateDistanceKm_같은_좌표면_0을_반환한다() {
        BigDecimal lat = new BigDecimal("37.5665");
        BigDecimal lng = new BigDecimal("126.9780");

        BigDecimal distance = GeoUtils.calculateDistanceKm(lat, lng, lat, lng);

        assertThat(distance).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("calculateDistanceKm - 적도 위 경도 1도 차이는 약 111.2km다 (소수 첫째자리 반올림)")
    void calculateDistanceKm_적도위_경도1도차이는_약111_2km다() {
        BigDecimal distance = GeoUtils.calculateDistanceKm(
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE
        );

        // R(6371.0km) * toRadians(1) ≈ 111.194936... -> 소수 첫째자리 반올림 시 111.2
        assertThat(distance).isEqualByComparingTo("111.2");
    }

    @Test
    @DisplayName("calculateDistanceKm - 거리 계산은 대칭적이다 (A→B == B→A)")
    void calculateDistanceKm_거리는_대칭적이다() {
        BigDecimal seoulLat = new BigDecimal("37.5665");
        BigDecimal seoulLng = new BigDecimal("126.9780");
        BigDecimal busanLat = new BigDecimal("35.1796");
        BigDecimal busanLng = new BigDecimal("129.0756");

        BigDecimal seoulToBusan = GeoUtils.calculateDistanceKm(seoulLat, seoulLng, busanLat, busanLng);
        BigDecimal busanToSeoul = GeoUtils.calculateDistanceKm(busanLat, busanLng, seoulLat, seoulLng);

        assertThat(seoulToBusan).isEqualByComparingTo(busanToSeoul);
        // 서울-부산 실거리(약 325km) 근처인지 대략적인 범위만 확인한다.
        assertThat(seoulToBusan.doubleValue()).isBetween(300.0, 350.0);
    }
}