package com.samhanair.logis.inventory.config;

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
 * X-Internal-Token 헤더가 제시된 요청에 한해 내부 호출자(slip-service 등 sibling 마이크로서비스)
 * 를 system-internal 계정으로 인증한다. inventory 의 모든 mutation endpoint 가 일반 사용자 호출
 * + 형제 서비스 호출을 함께 받기 때문에 path prefix 분리 없이 헤더 존재 여부만으로 분기한다.
 *
 * <p>토큰 미제시 → no-op (downstream {@link HeaderAuthenticationFilter} 가 X-User-* 헤더 처리).
 * 토큰 제시되었으나 불일치 → 401 + 응답 종료.
 * 토큰 일치 → ROLE_MASTER 권한의 system-internal principal 을 SecurityContext 에 주입 (모든
 * `@PreAuthorize` 매트릭스 통과). 보안은 X-Internal-Token shared secret 으로 강제되므로 충분.
 *
 * <p>본 필터는 product-service 의 동일 클래스 패턴을 가져왔으나 path prefix 분기 없이 모든 요청
 * 헤더를 검사한다는 점이 다르다 (slip→inventory 호출이 일반 endpoint 를 사용하기 때문).
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
            // 토큰 미제시 — 일반 사용자 흐름 (HeaderAuthenticationFilter 가 X-User-* 처리)
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

        // 내부 호출 인증 성공 — ROLE_MASTER 로 모든 mutation endpoint 통과
        var authority = new SimpleGrantedAuthority("ROLE_MASTER");
        var auth = new UsernamePasswordAuthenticationToken(INTERNAL_PRINCIPAL, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
