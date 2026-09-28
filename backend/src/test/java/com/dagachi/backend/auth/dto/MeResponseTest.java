package com.dagachi.backend.auth.dto;

import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * MeResponse.from() 단위 테스트.
 *
 * REQ-AUTH-05: 소속 기관이 없는 USER/ADMIN은 institutionId가 null이어야 하고,
 * INSTITUTION Role처럼 소속 기관이 있는 경우 institutionId가 채워져야 한다.
 */
class MeResponseTest {

    private User buildUser() {
        return User.create(
                "user@test.com", "encoded-pw", "홍길동",
                "닉네임", "010-1234-5678", UserGender.MALE
        );
    }

    @Test
    @DisplayName("REQ-AUTH-05 - from - 소속 기관이 없으면 institutionId는 null이다")
    void from_소속기관이_없으면_institutionId는_null이다() {
        User user = buildUser();

        MeResponse response = MeResponse.from(user);

        assertThat(response.institutionId()).isNull();
        assertThat(response.email()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("REQ-AUTH-05 - from - 소속 기관이 있으면 institutionId를 채운다")
    void from_소속기관이_있으면_institutionId를_채운다() {
        User user = buildUser();
        Institution institution = mock(Institution.class);
        given(institution.getId()).willReturn(77L);
        ReflectionTestUtils.setField(user, "institution", institution);

        MeResponse response = MeResponse.from(user);

        assertThat(response.institutionId()).isEqualTo(77L);
    }
}