package com.samhanair.logis.notification.config;

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
 * X-Internal-Token 헤더 인증 — 형제 service 또는 운영 admin 도구가 사용. 본 필터는
 * {@code /internal/**} prefix 요청만 검사하고, 그 외 경로 (admin / actuator 등) 는
 * 즉시 통과시켜 downstream {@link HeaderAuthenticationFilter} 가 X-User-* 헤더로 일반
 * 사용자 흐름을 처리.
 *
 * <p>토큰 일치 → ROLE_MASTER 권한으로 {@code /internal/**} endpoint 통과.
 * 토큰 미제시 → no-op (downstream 처리). 토큰 불일치 → 401 즉시 응답 (filter chain 단절).
 *
 * <p>partner-service / groupware-service 의 동일 prefix-한정 패턴과 일관 (PR #91 fix 패턴).
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
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
        if (path == null || !path.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (supplied == null || supplied.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String expected = properties.getToken();
        if (expected == null || expected.isBlank() || !expected.equals(supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"내부 인증 토큰이 유효하지 않습니다\"}");
            return;
        }

        var authority = new SimpleGrantedAuthority("ROLE_MASTER");
        var auth = new UsernamePasswordAuthenticationToken(INTERNAL_PRINCIPAL, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
