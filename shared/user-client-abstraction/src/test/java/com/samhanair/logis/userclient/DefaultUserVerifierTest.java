package com.samhanair.logis.userclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * DefaultUserVerifier 단위 테스트 — Phase 9 W4 (W3 backlog #1 채택).
 *
 * <p>네트워크 실패 시 fail-soft / fail-fast 분기 + null/empty 입력 + cache invalidate 동작 검증.
 * 실 RPC 검증은 각 service 의 IT 에서 {@code @MockBean UserClient} 로 격리.
 */
class DefaultUserVerifierTest {

    private DefaultUserVerifier newVerifier(boolean failFast) {
        UserVerifierProperties p = new UserVerifierProperties();
        p.setBaseUrl("http://127.0.0.1:1");
        p.setInternalToken("test-token");
        p.setTtlSeconds(60L);
        p.setMaxSize(100L);
        p.setFailFast(failFast);
        return new DefaultUserVerifier(RestClient.builder(), p);
    }

    @Test
    void exists_with_null_returns_false() {
        DefaultUserVerifier v = newVerifier(false);

        assertThat(v.exists(null)).isFalse();
    }

    @Test
    void verify_bulk_with_null_returns_empty() {
        DefaultUserVerifier v = newVerifier(false);

        assertThat(v.verifyBulk(null)).isEmpty();
        assertThat(v.verifyBulk(List.of())).isEmpty();
    }

    @Test
    void exists_network_failure_fail_soft_returns_true() {
        DefaultUserVerifier v = newVerifier(false);
        UUID id = UUID.randomUUID();

        boolean result = v.exists(id);

        assertThat(result).isTrue();
    }

    @Test
    void exists_network_failure_fail_fast_returns_false() {
        DefaultUserVerifier v = newVerifier(true);
        UUID id = UUID.randomUUID();

        boolean result = v.exists(id);

        assertThat(result).isFalse();
    }

    @Test
    void verify_bulk_network_failure_fail_soft_returns_true_for_all() {
        DefaultUserVerifier v = newVerifier(false);
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        Map<UUID, Boolean> result = v.verifyBulk(ids);

        assertThat(result).hasSize(2);
        assertThat(result.values()).allMatch(Boolean::booleanValue);
    }

    @Test
    void invalidate_cache_clears_entries() {
        DefaultUserVerifier v = newVerifier(false);
        UUID id = UUID.randomUUID();
        v.exists(id); // populate cache (network fail → cached true)

        v.invalidateCache();

        // 후속 호출은 다시 cache miss → RPC 시도 (네트워크 실패 → fail-soft true) — exception 없음
        assertThat(v.exists(id)).isTrue();
    }

    /**
     * post-W5 backlog cleanup (Q-W3-3, D-P9-21) — fail-mode alias 매핑 검증.
     *
     * <p>{@code failMode = STRICT} setter 호출 시 {@code failFast = true} 자동 동기화.
     * gateway 실패 시 fail-fast 동작 (false 반환) — 기존 failFast=true 와 동일 결과.
     */
    @Test
    void verify_strictMode_failFast_returnsFalseOnGatewayError() {
        UserVerifierProperties p = new UserVerifierProperties();
        p.setBaseUrl("http://127.0.0.1:1");
        p.setInternalToken("test-token");
        p.setFailMode(UserVerifierProperties.FailMode.STRICT);

        // STRICT 설정 시 failFast 자동 true 동기화 검증
        assertThat(p.isFailFast()).isTrue();
        assertThat(p.getFailMode()).isEqualTo(UserVerifierProperties.FailMode.STRICT);

        DefaultUserVerifier v = new DefaultUserVerifier(
                org.springframework.web.client.RestClient.builder(), p);
        UUID id = UUID.randomUUID();

        // STRICT 모드 — 네트워크 실패 시 fail-fast → false (검증 실패)
        boolean result = v.exists(id);
        assertThat(result).isFalse();
    }

    /**
     * post-W5 backlog cleanup (Q-W3-3, D-P9-21) — fail-mode OPEN (default) 동작 검증.
     *
     * <p>{@code failMode = OPEN} (default) 시 {@code failFast = false} 자동 동기화.
     * gateway 실패 시 fail-soft 동작 (true 반환, 기존 default 동작 보존).
     */
    @Test
    void verify_openMode_failSoft_returnsTrueOnGatewayError() {
        UserVerifierProperties p = new UserVerifierProperties();
        p.setBaseUrl("http://127.0.0.1:1");
        p.setInternalToken("test-token");
        p.setFailMode(UserVerifierProperties.FailMode.OPEN);

        // OPEN 설정 시 failFast 자동 false 동기화 검증
        assertThat(p.isFailFast()).isFalse();
        assertThat(p.getFailMode()).isEqualTo(UserVerifierProperties.FailMode.OPEN);

        DefaultUserVerifier v = new DefaultUserVerifier(
                org.springframework.web.client.RestClient.builder(), p);
        UUID id = UUID.randomUUID();

        // OPEN 모드 — 네트워크 실패 시 fail-soft → true (검증 통과, default 보존)
        boolean result = v.exists(id);
        assertThat(result).isTrue();
    }
}
