package com.samhanair.logis.dashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine cache manager — Phase 9 W4.
 *
 * <p>D-P9-12 (DevOps W3 backlog #4 채택) — Caffeine in-process 일관 유지. Phase 10 multi-instance
 * scaling 시점에 {@code samhan.cache.provider=redis} 토글로 전환 (별도 PR scope).
 *
 * <p>PR #94 W4 후속 fix (DevOps DV-W4-2) — provider 분기 가시성:
 * <ul>
 *   <li>{@code samhan.cache.provider=caffeine} (default) — 본 Bean 활성, in-process 캐시 동작</li>
 *   <li>{@code samhan.cache.provider=redis} — W4 시점 미구현. 부팅 직후 warn log 1줄 출력 후
 *       Caffeine fallback 동작 (Phase 10 multi-instance scaling 시점 정식 도입).</li>
 * </ul>
 * 관리자가 redis 토글로 잘못 운영 진입할 때 silent 동작을 방지하기 위한 명시 가시성.
 *
 * <p>관리되는 cache:
 * <ul>
 *   <li>{@code dashboard-kpi} — KPI 응답 (TTL = {@link DashboardCacheProperties.Kpi#getTtlSeconds()})</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    public static final String CACHE_KPI = "dashboard-kpi";

    private final DashboardCacheProperties props;

    /**
     * 부팅 시 provider 가시성 체크 — redis 토글 시 fallback 사실을 warn log 로 명시.
     * Phase 10 cutover 시점에 본 메서드는 정식 RedisCacheManager Bean 등록으로 대체.
     */
    @PostConstruct
    public void verifyProvider() {
        String provider = props.getProvider() == null ? "caffeine" : props.getProvider().toLowerCase();
        if ("redis".equals(provider)) {
            log.warn("samhan.cache.provider=redis 설정 감지. W4 단계에서는 Redis cache provider 가 미구현이며 "
                    + "Caffeine in-process 캐시로 fallback 합니다. 정식 도입은 Phase 10 multi-instance "
                    + "scaling 시점입니다 (D-P9-12).");
        } else if (!"caffeine".equals(provider)) {
            log.warn("samhan.cache.provider={} — 알 수 없는 값. Caffeine 으로 fallback.", provider);
        }
    }

    @Bean
    public CacheManager cacheManager(DashboardCacheProperties cacheProps) {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_KPI);
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheProps.getKpi().getTtlSeconds()))
                .maximumSize(cacheProps.getKpi().getMaxSize()));
        return mgr;
    }
}
