package com.dagachi.backend.common.security.config;

import com.dagachi.backend.common.security.jwt.JwtTokenProvider;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * 회원탈퇴 이후 기존 JWT를 계속 사용할 수 있는지 검증하는 통합 테스트입니다.
 *
 * 이 테스트는 Mockito로 인증 객체를 임의로 만들어 넣지 않고,
 * 실제 JwtTokenProvider가 발급한 JWT를 Authorization 헤더에 넣어
 * JwtAuthenticationFilter -> SecurityFilterChain -> Controller 흐름을 통과시킵니다.
 *
 * PostgreSQL Testcontainers를 사용하는 이유:
 * - JWT 발급 후 DB의 사용자 상태만 변경하는 실제 상황을 재현하기 위해서입니다.
 * - "JWT에 들어 있는 과거 인증 정보"와 "DB의 현재 계정 상태"가 다를 때
 *   서버가 현재 계정 상태를 다시 확인하는지를 검증합니다.
 *
 * 현재 MVP에서 실제로 구현된 계정 상태 변경은 회원탈퇴이므로
 * SUSPENDED가 아니라 WITHDRAWN + Soft Delete 상태를 기준으로 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLifecycleSecurityIntegrationTest extends PostgresContainerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴한 사용자는 탈퇴 전에 발급받은 JWT로 USER API에 접근할 수 없다")
    void withdrawn_user_cannot_use_previously_issued_token() throws Exception {

        /*
         * 1. 정상 ACTIVE USER를 DB에 저장합니다.
         *
         * User.create()는 실제 운영 코드와 동일하게
         * role=USER, status=ACTIVE, deleted=false 상태를 만듭니다.
         */
        User user = User.create(
                "withdrawn-security-test@example.com",
                "encoded-password",
                "보안 테스트 사용자",
                "security-test",
                "01012345678",
                UserGender.values()[0]
        );

        User savedUser = userRepository.saveAndFlush(user);

        /*
         * 2. 사용자가 ACTIVE 상태일 때 정상 JWT를 발급합니다.
         *
         * 이후 계정이 탈퇴되어도 이미 발급된 JWT 자체는
         * 서명과 만료시간 측면에서는 계속 유효할 수 있습니다.
         */
        String accessToken = jwtTokenProvider.createAccessToken(
                savedUser.getId(),
                savedUser.getRole().name()
        );

        /*
         * 3. 탈퇴 전에는 동일한 JWT로 USER 전용 API에
         * 정상 접근할 수 있는지 먼저 확인합니다.
         *
         * 이후 실패가 토큰 자체 문제가 아니라
         * 계정 상태 변경 때문인지 구분하기 위한 사전 검증입니다.
         */
        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk());

        /*
         * 4. 새로운 JWT를 발급하지 않고
         * DB의 현재 계정 상태만 실제 회원탈퇴 상태로 변경합니다.
         *
         * User.withdraw()의 실제 동작과 동일하게
         * status = WITHDRAWN
         * is_deleted = true
         * deleted_at = 현재 시각
         * 을 적용합니다.
         *
         * 테스트를 위해 운영 코드에 별도 setter를 추가하지 않고
         * Testcontainers PostgreSQL에서 상태만 직접 변경합니다.
         */
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE users
                   SET status = 'WITHDRAWN',
                       is_deleted = true,
                       deleted_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                savedUser.getId()
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "테스트 사용자 탈퇴 상태 변경에 실패했습니다. updatedRows=" + updatedRows
            );
        }

        /*
         * 5. 탈퇴 전에 발급받았던 "동일한 JWT"를 다시 사용합니다.
         *
         * 기대 동작:
         * 서버가 JWT claim만 신뢰하지 않고 DB의 현재 계정 상태를 다시 확인하여
         * 탈퇴한 사용자의 기존 JWT 요청을 인증 단계에서 거부해야 합니다.
         *
         * 현재 구현에서는 JwtAuthenticationFilter가 DB의 사용자 상태를 조회하지 않으므로,
         * 이 테스트는 실제 보안 취약점을 재현하는 실패 테스트가 될 가능성이 높습니다.
         */
        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isUnauthorized());
    }
}