package com.samhanair.logis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * X-Internal-Token 헤더 인증 — Phase 10 W10-4 (PR #99) DV-3 채택으로 13 service 통합 module 추출.
 *
 * <p>{@code path-prefix} 한정 (default {@code /internal/}). prefix 외 요청 = 즉시 통과 → downstream
 * {@code HeaderAuthenticationFilter} 가 X-User-* 헤더로 일반 사용자 흐름 처리.
 *
 * <p>토큰 일치 → {@code role} 권한 부여 (default ROLE_MASTER). 토큰 미제시 동작은 {@code allow-missing-token}
 * 분기:
 *
 * <ul>
 *   <li>{@code true} (default) — 11 service 표준, no-op (downstream 처리).
 *   <li>{@code false} — auth-service 표준, 즉시 401.
 * </ul>
 *
 * <p>토큰 불일치 → 401 즉시 응답 (filter chain 단절).
 *
 * <p>본 filter 는 servlet container 의 standard filter chain 에 자동 등록되지 않는다 — 각 service 의
 * {@code SecurityConfig} 에서 {@code .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)}
 * 로 명시적 등록. {@link InternalSecurityAutoConfiguration} 가 bean 으로 노출.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String INTERNAL_PRINCIPAL = "system-internal";

    private final InternalAuthProperties properties;

    public InternalTokenFilter(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String prefix = properties.getPathPrefix();
        if (prefix == null || prefix.isBlank()) {
            // properties 누락 가드 — prefix 없으면 모든 경로 통과 (downstream 처리)
            chain.doFilter(request, response);
            return;
        }
        if (path == null || !path.startsWith(prefix)) {
            chain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader(INTERNAL_TOKEN_HEADER);
        String expected = properties.getToken();

        if (supplied == null || supplied.isBlank()) {
            if (properties.isAllowMissingToken()) {
                // 11 service 표준: downstream 처리
                chain.doFilter(request, response);
                return;
            }
            // auth-service 표준: 즉시 401
            writeUnauthorized(response);
            return;
        }

        if (expected == null || expected.isBlank() || !expected.equals(supplied)) {
            writeUnauthorized(response);
            return;
        }

        String role = properties.getRole();
        if (role == null || role.isBlank()) {
            role = "MASTER";
        }
        var authority = new SimpleGrantedAuthority("ROLE_" + role);
        var auth = new UsernamePasswordAuthenticationToken(INTERNAL_PRINCIPAL, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"내부 인증 토큰이 유효하지 않습니다\"}");
    }
}
