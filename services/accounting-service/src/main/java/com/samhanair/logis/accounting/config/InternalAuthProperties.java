package com.samhanair.logis.accounting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared-secret used as {@code X-Internal-Token} when calling other services'
 * internal endpoints (Slice A 본 슬라이스에선 미사용 — A3 진입 시 ProductClient 등이 사용 예정).
 * Configured via {@code app.security.internal.token} (env override: {@code INTERNAL_AUTH_TOKEN}).
 */
@Data
@ConfigurationProperties(prefix = "app.security.internal")
public class InternalAuthProperties {

    private String token;
}
