package com.dagachi.backend.common.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.dagachi.backend.common.security.handler.JwtAuthenticationEntryPoint;
import org.springframework.security.authentication.BadCredentialsException;

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
// userId / role 추출
//  ↓
// Authentication 생성
//  ↓
// SecurityContextHolder에 저장
//  ↓
// 다음 Filter / Controller 진행

// OncePerRequestFilter: 요청 하나당 한 번만 실행되는 필터
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    // Authorization 헤더 없음
    // → 인증 없이 계속 진행
    // → Public API에서는 비회원 가능
    //
    // Authorization: Bearer 정상JWT
    // → SecurityContext에 userId/role 저장
    // → 회원 처리
    //
    // Authorization 헤더 있음 + 잘못된 형식/만료/변조 JWT
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

        // Authorization 헤더가 없을 때만 비인증 요청으로 그대로 진행합니다.
        // 공개 API에서는 이 경우 비회원 요청으로 처리할 수 있습니다.
        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더를 보냈지만 Bearer 형식이 아니거나,
        // 토큰이 만료·변조된 경우 비회원으로 처리하지 않고 401을 반환합니다.
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            jwtAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT")
            );
            return;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        String role = jwtTokenProvider.getRole(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}