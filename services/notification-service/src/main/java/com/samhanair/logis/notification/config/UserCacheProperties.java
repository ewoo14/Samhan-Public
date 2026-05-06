package com.samhanair.logis.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UserClient bulk verify Caffeine cache 설정 — Phase 9 W3 BE backlog #4 채택.
 *
 * <ul>
 *   <li>{@code ttlSeconds} — 단일 user verify 결과 TTL (기본 60초)</li>
 *   <li>{@code maxSize} — Caffeine maximumSize (기본 10000)</li>
 * </ul>
 *
 * <p>짧은 시간 반복 lookup (결재선 N 결재자 검증 등) 시 RPC 회피 — fan-out 직렬 호출을 한 번의 bulk
 * RPC + cache hit 로 단축.
 */
@Data
@ConfigurationProperties(prefix = "samhan.notification.user-cache")
public class UserCacheProperties {

    private long ttlSeconds = 60L;
    private long maxSize = 10000L;
}
