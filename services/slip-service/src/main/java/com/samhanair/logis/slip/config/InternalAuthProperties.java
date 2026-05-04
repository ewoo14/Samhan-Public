package com.samhanair.logis.slip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared-secret used as {@code X-Internal-Token} when calling other services'
 * {@code /products/internal/**} or {@code /inventory/**} endpoints. Configured via
 * {@code app.security.internal.token} (env override: {@code INTERNAL_AUTH_TOKEN}).
 */
@Data
@ConfigurationProperties(prefix = "app.security.internal")
public class InternalAuthProperties {

    private String token;
}
