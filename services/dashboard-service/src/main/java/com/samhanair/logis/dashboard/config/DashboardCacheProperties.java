package com.samhanair.logis.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대시보드 캐시 설정 — Phase 9 W4 (D-P9-12, DevOps W3 backlog #4 채택).
 *
 * <ul>
 *   <li>{@code provider} — {@code caffeine} (default) 또는 {@code redis} (Phase 10 multi-instance scaling 시점)</li>
 *   <li>{@code kpi.ttlSeconds} — KPI 응답 TTL (default 60s)</li>
 *   <li>{@code kpi.maxSize} — Caffeine maximumSize (default 5000)</li>
 * </ul>
 *
 * <p>본 단계는 Caffeine 일관 유지 + Redis 토글 약속만 보유 (실제 Redis impl 은 Phase 10).
 */
@Data
@ConfigurationProperties(prefix = "samhan.cache")
public class DashboardCacheProperties {

    private String provider = "caffeine";
    private Kpi kpi = new Kpi();

    @Data
    public static class Kpi {
        private long ttlSeconds = 60L;
        private long maxSize = 5000L;
    }
}
