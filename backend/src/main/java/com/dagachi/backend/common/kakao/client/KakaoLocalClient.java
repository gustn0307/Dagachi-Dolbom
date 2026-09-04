package com.dagachi.backend.common.kakao.client;

import com.dagachi.backend.common.kakao.dto.Coordinate;
import com.dagachi.backend.common.kakao.dto.KakaoAddressSearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class KakaoLocalClient {

    private final RestClient kakaoLocalRestClient;

    public KakaoLocalClient(
            @Qualifier("kakaoLocalRestClient")
            RestClient kakaoLocalRestClient
    ) {
        this.kakaoLocalRestClient = kakaoLocalRestClient;
    }

    /**
     * 주소를 Kakao Local API에 전달하여 위도/경도를 조회합니다.
     *
     * Kakao 응답에서는:
     * x = 경도(longitude)
     * y = 위도(latitude)
     *
     * 순서가 일반적인 latitude/longitude 표기와 반대이므로
     * Coordinate로 변환할 때 주의합니다.
     */
    public Coordinate searchCoordinate(
            String address
    ) {

        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException(
                    "좌표를 조회할 주소가 없습니다."
            );
        }

        KakaoAddressSearchResponse response =
                kakaoLocalRestClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v2/local/search/address.json")
                                .queryParam("query", address)
                                .build()
                        )
                        .retrieve()
                        .body(KakaoAddressSearchResponse.class);

        /*
         * API 호출은 성공했지만 주소 검색 결과가 없는 경우입니다.
         *
         * 존재하지 않는 주소나 Kakao가 인식하지 못하는 주소를
         * 임의 좌표로 처리하지 않고 명확하게 실패시킵니다.
         */
        if (response == null
                || response.documents() == null
                || response.documents().isEmpty()) {
            throw new IllegalStateException(
                    "기관 주소의 좌표를 찾을 수 없습니다."
            );
        }

        KakaoAddressSearchResponse.Document document =
                response.documents().getFirst();

        if (!StringUtils.hasText(document.x())
                || !StringUtils.hasText(document.y())) {
            throw new IllegalStateException(
                    "Kakao Local API가 유효하지 않은 좌표를 반환했습니다."
            );
        }

        try {
            return new Coordinate(
                    new BigDecimal(document.y()),
                    new BigDecimal(document.x())
            );
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Kakao Local API의 좌표 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }
}