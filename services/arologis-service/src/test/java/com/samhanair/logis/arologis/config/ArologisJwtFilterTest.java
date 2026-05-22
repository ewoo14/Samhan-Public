package com.samhanair.logis.arologis.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.arologis.domain.auth.AdminUserRole;
import com.samhanair.logis.arologis.service.auth.JwtIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class ArologisJwtFilterTest {

    private static final String SECRET_64 =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private ArologisJwtFilter filter;
    private JwtIssuer issuer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ArologisJwtProperties props = new ArologisJwtProperties();
        props.setSecret(SECRET_64);
        props.setIssuer("arologis-service");
        props.setAccessExpirySeconds(3600);
        issuer = new JwtIssuer(props);
        filter = new ArologisJwtFilter(issuer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwt_claim_headers_override_spoofed_x_user_headers_downstream() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = issuer.issueAccessForAdmin(userId, "manager", AdminUserRole.AROLOGIS_MANAGER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "AROLOGIS_MASTER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> downstreamRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> downstreamRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamRequest.get().getHeader("X-User-Id")).isEqualTo(userId.toString());
        assertThat(downstreamRequest.get().getHeader("X-User-Role")).isEqualTo("AROLOGIS_MANAGER");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_AROLOGIS_MANAGER");
    }

    @Test
    void cors_does_not_allow_browser_supplied_x_user_headers() throws Exception {
        Method method = SecurityConfig.class.getDeclaredMethod("corsConfigurationSource");
        method.setAccessible(true);
        CorsConfigurationSource source = (CorsConfigurationSource) method.invoke(new SecurityConfig());
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/auth/me");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "GET");

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedHeaders())
                .doesNotContain("*", "X-User-Id", "X-User-Role");
        assertThat(config.checkHeaders(List.of("Authorization", "Content-Type")))
                .containsExactly("Authorization", "Content-Type");
        assertThat(config.checkHeaders(List.of("X-User-Role"))).isNull();
    }
}
