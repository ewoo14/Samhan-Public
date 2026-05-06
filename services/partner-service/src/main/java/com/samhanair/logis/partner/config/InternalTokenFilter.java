package com.samhanair.logis.partner.config;

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
 * X-Internal-Token 헤더 인증 — slip-service 의 partnerCode → partnerId lookup 호출 또는
 * 운영 admin 도구가 사용. 토큰 미제시 → no-op (downstream {@link HeaderAuthenticationFilter}
 * 가 X-User-* 헤더로 일반 사용자 흐름을 처리).
 *
 * <p>토큰 일치 → ROLE_MASTER 권한으로 모든 {@code /internal/**} endpoint 통과.
 * 일치하지 않는 값이 제시된 경우 = 잘못된 호출자 → 401 즉시 응답 (downstream 필터 진입 차단).
 *
 * <p>partner-order-service 의 동일 패턴 (PR #76 M4) 을 가져왔다.
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
