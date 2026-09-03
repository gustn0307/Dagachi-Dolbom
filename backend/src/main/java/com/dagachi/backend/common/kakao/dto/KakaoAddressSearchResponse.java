package com.dagachi.backend.common.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Kakao 주소 검색 API의 응답 중
 * 현재 서비스에서 필요한 최소 필드만 매핑합니다.
 *
 * Kakao가 다른 필드를 추가로 반환하더라도
 * 필요한 좌표 값만 안정적으로 읽을 수 있도록
 * 알 수 없는 필드는 무시합니다.
 */

// 필요한 필드만 record에 매핑하고 나머지는 @JsonIgnoreProperties(ignoreUnknown = true)로 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressSearchResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String x,
            String y
    ) {
    }
}
