package com.samhanair.logis.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.samhanair.logis.dashboard.client.PartnerClient;
import com.samhanair.logis.dashboard.client.PartnerSummary;
import com.samhanair.logis.dashboard.config.CacheConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * PartnerCodeResolver 단위 테스트 — Phase 9 W5 신규 (D-P9-16, BE 의견 3 채택).
 *
 * <p>커버:
 * <ol>
 *   <li>resolveAll 빈 리스트 → 빈 Map 반환 + client 호출 0</li>
 *   <li>resolveAll 전체 cache miss → bulk client 1회 호출 + 결과 캐시 적재</li>
 *   <li>resolveAll 캐시 hit + miss 분리 → miss 만 client 호출</li>
 *   <li>resolveAll 일부 코드 미존재 → 매칭 항목만 결과 Map 에 포함</li>
 * </ol>
 *
 * <p>Cache 의존을 실제 {@link CaffeineCacheManager} 로 주입 — Spring 자동 unwrap 흐름과 본 W5
 * 직접 적재 흐름의 호환성 동시 검증.
 */
@ExtendWith(MockitoExtension.class)
class PartnerCodeResolverTest {

    @Mock
    private PartnerClient partnerClient;

    private CacheManager cacheManager;
    private PartnerCodeResolver resolver;

    @BeforeEach
    void setUp() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CacheConfig.CACHE_PARTNER_RESOLVE);
        mgr.setCaffeine(Caffeine.newBuilder().maximumSize(100));
        this.cacheManager = mgr;
        this.resolver = new PartnerCodeResolver(partnerClient, cacheManager);
    }

    @Test
    void resolveAll_with_empty_list_returns_empty_map_and_skips_client() {
        Map<String, UUID> result = resolver.resolveAll(List.of());

        assertThat(result).isEmpty();
        verify(partnerClient, never()).findByCodes(any());
    }

    @Test
    void resolveAll_with_all_miss_calls_bulk_client_once_and_populates_cache() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(partnerClient.findByCodes(eq(List.of("P-A", "P-B")))).thenReturn(List.of(
                new PartnerSummary(id1, "P-A", "거래처A"),
                new PartnerSummary(id2, "P-B", "거래처B")));

        Map<String, UUID> result = resolver.resolveAll(List.of("P-A", "P-B"));

        assertThat(result).containsEntry("P-A", id1).containsEntry("P-B", id2);
        verify(partnerClient, times(1)).findByCodes(any());

        // 캐시 적재 — 동일 코드 재호출 시 client 호출 0 회 보장
        Map<String, UUID> cached = resolver.resolveAll(List.of("P-A", "P-B"));
        assertThat(cached).containsEntry("P-A", id1).containsEntry("P-B", id2);
        verify(partnerClient, times(1)).findByCodes(any());
    }

    @Test
    void resolveAll_separates_hit_and_miss_calling_client_only_for_miss() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        // P-A 사전 cache 적재 — Optional<UUID> wrapper 형태 (단건 resolve 일관)
        cacheManager.getCache(CacheConfig.CACHE_PARTNER_RESOLVE).put("P-A", Optional.of(idA));
        when(partnerClient.findByCodes(eq(List.of("P-B")))).thenReturn(List.of(
                new PartnerSummary(idB, "P-B", "거래처B")));

        Map<String, UUID> result = resolver.resolveAll(List.of("P-A", "P-B"));

        assertThat(result).containsEntry("P-A", idA).containsEntry("P-B", idB);
        verify(partnerClient, times(1)).findByCodes(eq(List.of("P-B")));
    }

    @Test
    void resolveAll_with_partial_missing_returns_only_matched_codes() {
        UUID idA = UUID.randomUUID();
        when(partnerClient.findByCodes(eq(List.of("P-A", "P-MISS")))).thenReturn(List.of(
                new PartnerSummary(idA, "P-A", "거래처A")));

        Map<String, UUID> result = resolver.resolveAll(List.of("P-A", "P-MISS"));

        assertThat(result).containsEntry("P-A", idA);
        assertThat(result).doesNotContainKey("P-MISS");
    }
}
