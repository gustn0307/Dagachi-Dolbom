package com.dagachi.backend.common.util;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 대상자 주소·출생연도를 공개용 요약 정보(동 단위 지역, 나이대)로 변환한다.
 *
 * CareRecipient에 별도 '동' 컬럼이 없어 address 문자열에서 파싱한다.
 * 완벽하지 않을 수 있으므로 팀 확인 후 전용 컬럼 추가를 검토할 수 있다.
 */
public final class AddressUtils {

    private static final Pattern DONG_PATTERN = Pattern.compile("(\\S*[동리읍면])(?=\\s|$)");

    private AddressUtils() {
    }

    public static String extractRegion(String address) {
        if (address == null || address.isBlank()) {
            return "지역 정보 없음";
        }

        Matcher matcher = DONG_PATTERN.matcher(address);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }

        return lastMatch != null ? lastMatch : "지역 정보 없음";
    }

    public static String calculateAgeGroup(Integer birthYear) {
        if (birthYear == null) {
            return "연령 정보 없음";
        }

        int age = LocalDate.now().getYear() - birthYear;
        int decade = (age / 10) * 10;

        return decade + "대";
    }
}