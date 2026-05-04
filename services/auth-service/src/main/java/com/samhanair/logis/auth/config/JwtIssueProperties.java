package com.samhanair.logis.auth.config;

import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for JWT issuance ({@code app.security.jwt.*}). */
@Data
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtIssueProperties {

    private String secret;
    private long ttlSeconds;

    public byte[] getSecretBytes() {
        return secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }
}
