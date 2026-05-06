package com.samhanair.logis.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import com.samhanair.logis.notification.config.UserCacheProperties;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * UserClient bulk verify + Caffeine cache 단위 테스트 — Phase 9 W3 BE backlog #4 채택.
 *
 * <p>커버 3 case:
 * <ol>
 *   <li>verifyBulk — null / empty 입력 → empty map</li>
 *   <li>cache hit — 캐시에 미리 적재된 ID 는 RPC skip (network 호출 없이 cache hit 매핑 반환)</li>
 *   <li>verifyBulk 모두 cache hit 인 경우 → RPC skip (네트워크 미가용 환경 통과)</li>
 * </ol>
 *
 * <p>RestClient.Builder 는 실 빌더 사용 — 호출 자체가 발생하지 않는 cache-hit-only 흐름만 검증.
 * 실 RPC 응답 파싱 흐름은 IT 의 Spring 부팅 단계에서 검증 (mock UserClient).
 */
class UserClientBulkVerifyTest {

    private UserClient client;
    private Cache<UUID, Boolean> cacheRef;

    @BeforeEach
    void setup() throws Exception {
        UserCacheProperties props = new UserCacheProperties();
        props.setTtlSeconds(60L);
        props.setMaxSize(1000L);

        // ServiceDiscoveryClient — 메소드 호출 안 되므로 더미 구현 (anonymous)
        ServiceDiscoveryClient discovery = new ServiceDiscoveryClient() {
            @Override
            public void register(String serviceName, String host, int port) {
            }

            @Override
            public void deregister(String serviceName) {
            }

            @Override
            public java.util.List<com.samhanair.logis.discovery.ServiceInstance> lookup(String serviceName) {
                return java.util.List.of();
            }

            @Override
            public boolean healthcheck(String serviceName) {
                return false;
            }
        };

        client = new UserClient(RestClient.builder(), discovery,
                "http://localhost:9999", "test-token", props);
        // PostConstruct 수동 호출 (Spring 부팅 없음)
        java.lang.reflect.Method initCache = UserClient.class.getDeclaredMethod("initCache");
        initCache.setAccessible(true);
        initCache.invoke(client);

        // 캐시 직접 접근 — 테스트 fixture 적재
        Field f = UserClient.class.getDeclaredField("existsCache");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<UUID, Boolean> ref = (Cache<UUID, Boolean>) f.get(client);
        cacheRef = ref;
    }

    @Test
    void verifyBulk_with_null_or_empty_returns_empty_map() {
        assertThat(client.verifyBulk(null)).isEmpty();
        assertThat(client.verifyBulk(List.of())).isEmpty();
    }

    @Test
    void verifyBulk_all_cache_hit_skips_rpc() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        cacheRef.put(id1, true);
        cacheRef.put(id2, false);

        Map<UUID, Boolean> result = client.verifyBulk(List.of(id1, id2));

        // 모두 cache hit — 네트워크 호출 없이 cache value 반환
        assertThat(result).hasSize(2);
        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
    }

    @Test
    void exists_caches_lookup_result() {
        // 외부 RPC 가 사용 불가하지만 client 의 fallback 정책 (skeleton 통과) 로 true 반환 후 cache 적재.
        UUID id = UUID.randomUUID();

        boolean first = client.exists(id);
        // 본 client 의 skeleton 정책: 네트워크 실패 시 true (PR #92 패턴 일관)
        assertThat(first).isTrue();

        // 동일 호출 재시도 → cache hit (네트워크 실패 안남, 동일 결과)
        Boolean cached = cacheRef.getIfPresent(id);
        assertThat(cached).isNotNull();
        assertThat(cached).isTrue();
        assertThat(client.exists(id)).isTrue();
    }
}
