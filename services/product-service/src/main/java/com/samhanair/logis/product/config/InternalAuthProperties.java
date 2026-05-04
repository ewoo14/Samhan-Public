package com.samhanair.logis.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared-secret used to authenticate internal service-to-service calls hitting
 * {@code /products/internal/**}. Configured via {@code app.security.internal.token}
 * (env override: {@code INTERNAL_AUTH_TOKEN}).
 */
@Data
@ConfigurationProperties(prefix = "app.security.internal")
public class InternalAuthProperties {

    private String token;
}
