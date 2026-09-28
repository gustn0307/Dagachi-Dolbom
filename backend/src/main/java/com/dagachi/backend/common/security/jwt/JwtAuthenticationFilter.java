package com.dagachi.backend.common.security.jwt;

import com.dagachi.backend.common.security.handler.JwtAuthenticationEntryPoint;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


// HTTP 요청
//  ↓
// Authorization 헤더 확인
//  ↓
// Bearer 토큰 추출
//  ↓
// JwtTokenProvider.validateToken()
//  ↓
// JWT의 userId 추출
//  ↓
// DB에서 현재 사용자 상태 재확인
//  ↓
// deleted=false + ACTIVE인지 확인
//  ↓
// DB의 현재 role로 Authentication 생성
//  ↓
// SecurityContextHolder에 저장
//  ↓
// 다음 Filter / Controller 진행

/**
 * JWT 기반 인증을 처리하는 필터입니다.
 *
 * 중요한 보안 정책:
 * JWT가 서명/만료 검증을 통과했다고 해서 현재 계정까지
 * 유효하다고 판단하지 않습니다.
 *
 * JWT는 발급 당시의 정보를 가지고 있기 때문에,
 * 토큰 발급 이후 사용자가 탈퇴하거나 계정 상태/권한이 변경될 수 있습니다.
 *
 * 따라서 JWT에서 userId를 얻은 뒤 DB의 현재 사용자 상태를 다시 조회하여
 * deleted=false && status=ACTIVE인 경우에만 인증 객체를 생성합니다.
 *
 * Authority 또한 JWT의 과거 role claim이 아니라
 * DB에 저장된 현재 role을 사용합니다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            UserRepository userRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userRepository = userRepository;
    }

    // Authorization 헤더 없음
    // → 인증 없이 계속 진행
    // → Public API에서는 비회원 가능
    //
    // Authorization: Bearer 정상JWT
    // → JWT 검증
    // → DB에서 현재 사용자 조회
    // → ACTIVE + deleted=false 확인
    // → 현재 DB Role로 SecurityContext 설정
    //
    // Authorization 헤더 있음 + 잘못된 형식/만료/변조 JWT
    // 또는 탈퇴·삭제·비활성 계정
    // → 401 AUTH_401
    // → Controller 진입 안 함
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(AUTHORIZATION_HEADER);

        String token = resolveToken(request);

        /*
         * Authorization 헤더 자체가 없는 경우에는
         * 비인증 요청으로 그대로 진행합니다.
         *
         * 공개 API에서는 이 경우 비회원 요청이 허용될 수 있고,
         * 인증이 필요한 API라면 이후 SecurityFilterChain에서 401 처리됩니다.
         */
        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * 클라이언트가 Authorization 헤더를 보냈다면
         * 잘못된 Bearer 형식이나 만료·변조된 JWT를
         * 비회원 요청으로 조용히 처리하지 않습니다.
         */
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            rejectAuthentication(request, response);
            return;
        }

        Long userId;

        try {
            /*
             * validateToken()이 JWT 서명/만료 검증을 담당하지만,
             * subject가 실제 Long userId 형식인지까지는 별도 확인이 필요합니다.
             *
             * 비정상 subject가 들어온 경우 인증 실패로 처리하여
             * NumberFormatException 등이 500으로 노출되지 않도록 합니다.
             */
            userId = jwtTokenProvider.getUserId(token);
        } catch (RuntimeException exception) {
            rejectAuthentication(request, response);
            return;
        }

        /*
         * JWT는 발급 당시 상태를 가지고 있으므로
         * DB의 현재 계정 상태를 반드시 다시 확인합니다.
         *
         * findByIdAndDeletedFalse():
         * - 탈퇴/삭제된 계정은 조회되지 않음
         *
         * status == ACTIVE:
         * - 현재 실제 서비스 사용이 가능한 계정만 인증 허용
         *
         * 이를 통해 회원탈퇴 전에 발급된 JWT가 남아 있어도
         * Controller에 도달하기 전에 요청을 차단할 수 있습니다.
         */
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElse(null);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            rejectAuthentication(request, response);
            return;
        }

        /*
         * Role 역시 JWT claim을 그대로 신뢰하지 않고
         * DB에 저장된 현재 Role을 기준으로 Authority를 구성합니다.
         *
         * 따라서 토큰 발급 이후 권한이 변경되더라도
         * 과거 Role을 계속 사용할 수 없습니다.
         */
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getId(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + user.getRole().name()
                                )
                        )
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * JWT 또는 현재 계정 상태가 인증 조건을 충족하지 못한 경우
     * 프로젝트의 공통 JWT 401 응답 형식을 사용합니다.
     */
    private void rejectAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException, ServletException {

        SecurityContextHolder.clearContext();

        jwtAuthenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("Invalid JWT or inactive account")
        );
    }

    private String resolveToken(HttpServletRequest request) {

        String bearerToken =
                request.getHeader(AUTHORIZATION_HEADER);

        if (bearerToken != null
                && bearerToken.startsWith(BEARER_PREFIX)) {

            return bearerToken.substring(
                    BEARER_PREFIX.length()
            );
        }

        return null;
    }
}