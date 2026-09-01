package com.dagachi.backend.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 위경도 기반 거리 계산 유틸리티.
 * Haversine 공식을 사용하여 두 지점 간 거리를 km 단위로 계산한다.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static BigDecimal calculateDistanceKm(
            BigDecimal lat1,
            BigDecimal lng1,
            BigDecimal lat2,
            BigDecimal lng2
    ) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }

        double radLat1 = Math.toRadians(lat1.doubleValue());
        double radLat2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceKm = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP);
    }
}