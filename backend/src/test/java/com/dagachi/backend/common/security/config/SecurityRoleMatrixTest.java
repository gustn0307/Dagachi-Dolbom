package com.dagachi.backend.common.security.config;

import com.dagachi.backend.common.security.handler.JwtAccessDeniedHandler;
import com.dagachi.backend.common.security.handler.JwtAuthenticationEntryPoint;
import com.dagachi.backend.common.security.jwt.JwtAuthenticationFilter;
import com.dagachi.backend.common.security.jwt.JwtTokenProvider;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.mypage.controller.UserProfileController;
import com.dagachi.backend.user.mypage.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * SecurityConfig의 USER Role 접근 제어를 실제 HTTP 흐름으로 검증합니다.
 *
 * 검증 대상:
 * - 익명 사용자는 USER 전용 API 접근 불가 → 401
 * - USER는 USER 전용 API 접근 가능 → 200
 * - INSTITUTION은 USER 전용 API 접근 불가 → 403
 * - ADMIN은 USER 전용 API 접근 불가 → 403
 *
 * 단순히 @WithMockUser로 SecurityContext를 직접 만들어 넣지 않고,
 *
 * Authorization 헤더
 * -> JwtAuthenticationFilter
 * -> SecurityFilterChain
 * -> Controller
 *
 * 흐름을 통과시켜 실제 SecurityConfig의 URL/Role 정책을 검증합니다.
 *
 * JwtTokenProvider 자체의 서명·만료 검증은 이 테스트의 목적이 아니므로 Mock합니다.
 *
 * JwtAuthenticationFilter는 현재 JWT의 userId만 신뢰하고,
 * 실제 Role과 계정 상태는 DB의 현재 사용자 정보를 기준으로 결정합니다.
 * 따라서 이 WebMvcTest에서는 UserRepository도 Mock하여
 * 각 테스트 사용자의 현재 상태와 Role을 명시적으로 제공합니다.
 */
@WebMvcTest(UserProfileController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class SecurityRoleMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * JWT 자체의 생성·서명 검증이 아니라
     * JWT 인증 이후 Role 인가 규칙을 검증하는 테스트이므로 Mock합니다.
     */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /**
     * JwtAuthenticationFilter가 토큰의 userId로 현재 사용자를 조회하므로
     * WebMvcTest에서도 Repository Mock이 필요합니다.
     *
     * 실제 PostgreSQL 조회 동작은 Repository/Integration 테스트의 책임이고,
     * 여기서는 SecurityFilterChain의 Role Matrix만 검증합니다.
     */
    @MockitoBean
    private UserRepository userRepository;

    /**
     * Controller가 생성되기 위해 필요한 Service입니다.
     *
     * 이 테스트에서는 USER Role 요청이 Controller까지 통과했는지만 확인하므로
     * 실제 비즈니스 로직은 검증하지 않습니다.
     */
    @MockitoBean
    private UserProfileService userProfileService;

    /**
     * @WebMvcTest는 JPA 전체 Context를 로드하지 않지만,
     * 애플리케이션의 JPA Auditing 설정에서 jpaMappingContext Bean을 요구합니다.
     *
     * Security HTTP 테스트에서는 JPA 자체를 검증하지 않으므로 Mock으로 대체합니다.
     */
    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;


    @Test
    @DisplayName("익명 사용자는 USER 전용 API에 접근할 수 없다")
    void anonymous_cannot_access_user_api() throws Exception {

        /*
         * Authorization 헤더가 없으면 JwtAuthenticationFilter는
         * 인증 객체를 생성하지 않고 다음 필터로 진행합니다.
         *
         * /api/users/me는 USER Role 전용이므로
         * SecurityFilterChain에서 401이 반환되어야 합니다.
         */
        mockMvc.perform(
                        get("/api/users/me")
                )
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("USER는 USER 전용 API에 접근할 수 있다")
    void user_can_access_user_api() throws Exception {

        /*
         * JWT에서 userId=1을 얻고,
         * DB의 현재 사용자가 ACTIVE + USER라고 가정합니다.
         */
        givenValidToken(
                "user-token",
                1L,
                UserRole.USER
        );

        /*
         * USER Role은 /api/users/me 접근이 허용되어
         * Controller까지 정상 진입해야 합니다.
         */
        given(userProfileService.getMyProfile(1L))
                .willReturn(null);

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer user-token"
                                )
                )
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("INSTITUTION은 USER 전용 API에 접근할 수 없다")
    void institution_cannot_access_user_api() throws Exception {

        /*
         * JWT 자체는 정상이고 계정도 ACTIVE이지만
         * 현재 DB Role이 INSTITUTION인 상황을 만듭니다.
         */
        givenValidToken(
                "institution-token",
                2L,
                UserRole.INSTITUTION
        );

        /*
         * 인증(Authentication)은 성공하지만,
         * USER Role 전용 URL의 인가(Authorization)는 실패해야 하므로 403입니다.
         */
        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer institution-token"
                                )
                )
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("ADMIN은 USER 전용 API에 접근할 수 없다")
    void admin_cannot_access_user_api() throws Exception {

        /*
         * JWT 자체는 정상이고 계정도 ACTIVE이지만
         * 현재 DB Role이 ADMIN인 상황을 만듭니다.
         */
        givenValidToken(
                "admin-token",
                3L,
                UserRole.ADMIN
        );

        /*
         * ADMIN이라고 해서 모든 API를 자동으로 허용하는 것이 아니라,
         * USER 전용 API는 USER Role만 접근할 수 있어야 합니다.
         */
        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer admin-token"
                                )
                )
                .andExpect(status().isForbidden());
    }


    /**
     * Role 테스트마다 반복되는 JWT + 현재 DB 사용자 상태 Mock을 구성합니다.
     *
     * 중요한 점은 Role을 jwtTokenProvider.getRole()에서 제공하지 않는다는 것입니다.
     *
     * 현재 JwtAuthenticationFilter는 JWT의 과거 Role claim이 아니라
     * UserRepository에서 조회한 현재 User의 Role을 Authority로 사용합니다.
     *
     * 따라서 이 helper는:
     *
     * 1. JWT가 유효함
     * 2. JWT subject에서 userId를 얻음
     * 3. 해당 userId의 현재 계정은 ACTIVE
     * 4. 현재 DB Role은 테스트에서 지정한 Role
     *
     * 상태를 구성합니다.
     */
    private void givenValidToken(
            String token,
            Long userId,
            UserRole role
    ) {

        given(jwtTokenProvider.validateToken(token))
                .willReturn(true);

        given(jwtTokenProvider.getUserId(token))
                .willReturn(userId);

        /*
         * User Entity의 생성 로직 자체를 검증하는 테스트가 아니므로
         * Entity는 Mockito Mock을 사용합니다.
         *
         * 특히 User.create()는 일반 USER를 생성하는 팩토리이므로
         * INSTITUTION/ADMIN Role Matrix 테스트에 억지로 사용할 필요가 없습니다.
         */
        User user = mock(User.class);

        given(user.getId())
                .willReturn(userId);

        given(user.getStatus())
                .willReturn(UserStatus.ACTIVE);

        given(user.getRole())
                .willReturn(role);

        given(userRepository.findByIdAndDeletedFalse(userId))
                .willReturn(Optional.of(user));
    }
}