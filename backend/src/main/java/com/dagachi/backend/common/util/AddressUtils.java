package com.dagachi.backend.common.util;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressUtils {

    // 1순위: 동/리/읍/면 + "매산로1가", "종로2가" 같은 숫자+가로 끝나는 구시가지 법정동 이름
    private static final Pattern DONG_PATTERN =
            Pattern.compile("(\\S*(?:[동리읍면]|\\d가))(?=\\s|$)");

    // 2순위: "테헤란로", "강남대로", "역삼로7길"처럼 로/길로 끝나는 도로명
    private static final Pattern ROAD_PATTERN =
            Pattern.compile("(\\S*(?:로|길))(?=\\s|$)");

    // 3순위: 구/군
    private static final Pattern GU_PATTERN = Pattern.compile("(\\S*[구군])(?=\\s|$)");

    private static final Map<String, Integer> AGE_GROUP_BUCKETS = Map.of(
            "50대 이하", 50,
            "60대", 60,
            "70대", 70,
            "80대", 80,
            "90대 이상", 90
    );

    private AddressUtils() {
    }

    /**
     * 주소에서 표시용 지역 라벨을 추출한다.
     * 1) "동/리/읍/면" 또는 "OO로N가"(구시가지 법정동 명명 방식) 우선
     * 2) 없으면 "로/길/대로"로 끝나는 도로명으로 fallback (순수 도로명 주소)
     * 3) 그것도 없으면 "구/군" 단위로 fallback
     * 4) 그것도 없으면 "지역 정보 없음"
     *
     * [팀 확인 필요] 정규식 기반 파싱은 완전하지 않다.
     * 제주도 특수 리 명칭, 세종시 리 정착촌 등 예외 케이스가 추가로 나올 수 있다.
     */
    public static String extractRegion(String address) {
        if (address == null || address.isBlank()) {
            return "지역 정보 없음";
        }

        String dong = findLastMatch(DONG_PATTERN, address);
        if (dong != null) {
            return dong;
        }

        String road = findLastMatch(ROAD_PATTERN, address);
        if (road != null) {
            return road;
        }

        String gu = findLastMatch(GU_PATTERN, address);
        if (gu != null) {
            return gu;
        }

        return "지역 정보 없음";
    }

    private static String findLastMatch(Pattern pattern, String address) {
        Matcher matcher = pattern.matcher(address);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch;
    }

    public static String calculateAgeGroup(Integer birthYear) {
        if (birthYear == null) {
            return "연령 정보 없음";
        }

        int age = LocalDate.now().getYear() - birthYear;

        if (age < 60) return "50대 이하";
        if (age < 70) return "60대";
        if (age < 80) return "70대";
        if (age < 90) return "80대";
        return "90대 이상";
    }

    public static Integer parseAgeGroupBucket(String label) {
        return AGE_GROUP_BUCKETS.get(label);
    }
}