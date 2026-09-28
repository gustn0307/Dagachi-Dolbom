package com.dagachi.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AddressUtils 순수 유틸리티 단위 테스트. Spring/DB 없이 실행 가능하다.
 */
class AddressUtilsTest {

    // ---------------------------------------------------------------
    // extractRegion
    // ---------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("REQ-ACT-07 - extractRegion - 주소가 없으면 '지역 정보 없음'을 반환한다")
    void extractRegion_주소가_없으면_기본값을_반환한다(String address) {
        assertThat(AddressUtils.extractRegion(address)).isEqualTo("지역 정보 없음");
    }

    @Test
    @DisplayName("REQ-ACT-07 - extractRegion - 동/리/읍/면으로 끝나는 법정동이 있으면 우선 사용한다")
    void extractRegion_동으로_끝나면_동을_우선한다() {
        assertThat(AddressUtils.extractRegion("서울특별시 강남구 역삼동 123-45"))
                .isEqualTo("역삼동");
    }

    @Test
    @DisplayName("REQ-ACT-07 - extractRegion - 숫자+가로 끝나는 구시가지 법정동도 동과 동일하게 인식한다")
    void extractRegion_숫자가로_끝나면_인식한다() {
        assertThat(AddressUtils.extractRegion("서울특별시 종로구 종로2가 15"))
                .isEqualTo("종로2가");
    }

    @Test
    @DisplayName("REQ-ACT-07 - extractRegion - 동/리/읍/면이 없으면 로/길로 끝나는 도로명으로 대체한다")
    void extractRegion_동이_없으면_도로명으로_대체한다() {
        assertThat(AddressUtils.extractRegion("서울특별시 강남구 테헤란로 152"))
                .isEqualTo("테헤란로");
    }

    @Test
    @DisplayName("REQ-ACT-07 - extractRegion - 동/도로명이 모두 없으면 구/군으로 대체한다")
    void extractRegion_동과_도로명이_없으면_구군으로_대체한다() {
        assertThat(AddressUtils.extractRegion("부산광역시 해운대구"))
                .isEqualTo("해운대구");
    }

    @Test
    @DisplayName("REQ-ACT-07 - extractRegion - 아무 패턴도 매칭되지 않으면 '지역 정보 없음'을 반환한다")
    void extractRegion_매칭되는_패턴이_없으면_기본값을_반환한다() {
        assertThat(AddressUtils.extractRegion("정보없음")).isEqualTo("지역 정보 없음");
    }

    // ---------------------------------------------------------------
    // calculateAgeGroup
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-ACT-07 - calculateAgeGroup - birthYear가 null이면 '연령 정보 없음'을 반환한다")
    void calculateAgeGroup_출생연도가_없으면_기본값을_반환한다() {
        assertThat(AddressUtils.calculateAgeGroup(null)).isEqualTo("연령 정보 없음");
    }

    @Test
    @DisplayName("REQ-ACT-07 - calculateAgeGroup - 만 59세(60세 미만)는 '50대 이하'다")
    void calculateAgeGroup_59세는_50대이하다() {
        int currentYear = LocalDate.now().getYear();
        assertThat(AddressUtils.calculateAgeGroup(currentYear - 59)).isEqualTo("50대 이하");
    }

    @Test
    @DisplayName("REQ-ACT-07 - calculateAgeGroup - 만 60세는 '60대'다 (경계값)")
    void calculateAgeGroup_60세_경계값은_60대다() {
        int currentYear = LocalDate.now().getYear();
        assertThat(AddressUtils.calculateAgeGroup(currentYear - 60)).isEqualTo("60대");
    }

    @Test
    @DisplayName("REQ-ACT-07 - calculateAgeGroup - 만 70세는 '70대'다 (경계값)")
    void calculateAgeGroup_70세_경계값은_70대다() {
        int currentYear = LocalDate.now().getYear();
        assertThat(AddressUtils.calculateAgeGroup(currentYear - 70)).isEqualTo("70대");
    }

    @Test
    @DisplayName("REQ-ACT-07 - calculateAgeGroup - 만 90세 이상은 '90대 이상'이다 (경계값)")
    void calculateAgeGroup_90세_이상은_90대이상이다() {
        int currentYear = LocalDate.now().getYear();
        assertThat(AddressUtils.calculateAgeGroup(currentYear - 90)).isEqualTo("90대 이상");
        assertThat(AddressUtils.calculateAgeGroup(currentYear - 105)).isEqualTo("90대 이상");
    }

    // ---------------------------------------------------------------
    // parseAgeGroupBucket
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "50대 이하, 50",
            "60대, 60",
            "70대, 70",
            "80대, 80",
            "90대 이상, 90"
    })
    @DisplayName("REQ-ACT-06 - parseAgeGroupBucket - 허용된 라벨은 대표 버킷값으로 변환한다")
    void parseAgeGroupBucket_허용된_라벨은_버킷값을_반환한다(String label, Integer expected) {
        assertThat(AddressUtils.parseAgeGroupBucket(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100대", "10대", "", "50대"})
    @DisplayName("REQ-ACT-06 - parseAgeGroupBucket - 허용되지 않은 라벨은 null을 반환한다")
    void parseAgeGroupBucket_허용되지_않은_라벨은_null을_반환한다(String label) {
        assertThat(AddressUtils.parseAgeGroupBucket(label)).isNull();
    }
}