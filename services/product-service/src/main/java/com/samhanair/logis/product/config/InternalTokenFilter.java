package com.samhanair.logis.product.config;

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
 * Guards {@code /products/internal/**}: requires the {@code X-Internal-Token} header
 * to match the configured shared secret. On success, populates the SecurityContext
 * with a synthetic {@code system-internal} principal carrying ROLE_INTERNAL so the
 * downstream {@code @PreAuthorize} (none today, but reserved) and JPA auditing fire.
 *
 * <p>Skipped for non-internal paths so the regular {@link HeaderAuthenticationFilter}
 * keeps owning gateway-trusted ingress.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/products/internal/";
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

        String expected = properties.getToken();
        String supplied = request.getHeader(INTERNAL_TOKEN_HEADER);

        if (expected == null || expected.isBlank() || supplied == null || !expected.equals(supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"내부 인증 토큰이 유효하지 않습니다\"}");
            return;
        }

        var authority = new SimpleGrantedAuthority("ROLE_INTERNAL");
        var auth = new UsernamePasswordAuthenticationToken(INTERNAL_PRINCIPAL, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
