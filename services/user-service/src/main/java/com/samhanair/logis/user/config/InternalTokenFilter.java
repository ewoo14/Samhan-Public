package com.samhanair.logis.user.config;

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
 * X-Internal-Token 헤더 인증 — 형제 service (notification-service / groupware-service /
 * partner-service / partner-order-service / slip-service) 가 user-service 의 {@code /internal/**}
 * endpoint 호출 시 사용.
 *
 * <p>Phase 9 W3 신규 — Phase 9 W1/W2 의 UserClient 가 호출하는 단건 lookup endpoint
 * ({@code GET /internal/users/{userId}}) + W3 추가 bulk verify endpoint
 * ({@code POST /internal/users/verify-bulk}) 인증 보호.
 *
 * <p>토큰 일치 → ROLE_MASTER 권한으로 {@code /internal/**} endpoint 통과.
 * 토큰 미제시 → no-op (downstream 처리). 토큰 불일치 → 401 즉시 응답.
 *
 * <p>partner-service / groupware-service / notification-service 의 동일 prefix-한정 패턴 일관.
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
