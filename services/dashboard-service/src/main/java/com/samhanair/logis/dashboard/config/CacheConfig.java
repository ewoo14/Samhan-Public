package com.samhanair.logis.dashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
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
 * <p>관리되는 cache:
 * <ul>
 *   <li>{@code dashboard-kpi} — KPI 응답 (TTL = {@link DashboardCacheProperties.Kpi#getTtlSeconds()})</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_KPI = "dashboard-kpi";

    @Bean
    public CacheManager cacheManager(DashboardCacheProperties props) {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_KPI);
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getKpi().getTtlSeconds()))
                .maximumSize(props.getKpi().getMaxSize()));
        return mgr;
    }
}
