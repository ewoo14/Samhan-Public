package com.samhanair.logis.gateway.config;

import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound JWT settings — secret key + token TTL — read from
 * {@code app.security.jwt.*}. Mirrors the equivalent properties on
 * {@code auth-service} so both sides sign / verify with the same material.
 */
@Data
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** HMAC secret. Must be at least 32 bytes for HS256. */
    private String secret;

    /** Access-token lifetime in seconds. Default: 1 hour. */
    private long ttlSeconds = 3600;

    public byte[] getSecretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
