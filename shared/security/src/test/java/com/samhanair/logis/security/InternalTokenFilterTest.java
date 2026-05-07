package com.samhanair.logis.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * shared:security InternalTokenFilter 단위 테스트 — Phase 10 W10-4 (PR #99) DV-3 채택.
 *
 * <p>4 case + auth-service 호환 case 2 (allow-missing-token=false / role=INTERNAL).
 */
class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token-2026";

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prefix_path_with_matching_token_grants_role_master_and_passes() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/users/123");
        req.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_MASTER");
    }

    @Test
    void prefix_path_with_missing_token_passes_when_allow_missing_token_true() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        // default allow-missing-token = true
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/users/123");
        // 토큰 미제시
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200); // chain 통과
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void prefix_path_with_mismatched_token_returns_401() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/users/123");
        req.addHeader("X-Internal-Token", "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void non_prefix_path_passes_without_authentication() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/users/me"); // /internal/ 아님
        req.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        // 토큰을 보냈어도 prefix 외 경로 = ROLE_MASTER 부여 X (admin endpoint 우회 차단)
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void auth_service_compat_missing_token_returns_401_when_allow_missing_token_false() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        props.setPathPrefix("/auth/internal/");
        props.setRole("INTERNAL");
        props.setAllowMissingToken(false); // auth-service 표준
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/internal/accounts/1");
        // 토큰 미제시
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void auth_service_compat_matching_token_grants_role_internal() throws Exception {
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        props.setPathPrefix("/auth/internal/");
        props.setRole("INTERNAL");
        props.setAllowMissingToken(false);
        InternalTokenFilter filter = new InternalTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/internal/accounts/1");
        req.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_INTERNAL");
    }
}
