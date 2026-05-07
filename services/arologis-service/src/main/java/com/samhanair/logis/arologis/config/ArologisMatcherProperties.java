package com.samhanair.logis.arologis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DriverMatcher provider 설정 — Phase 10 W10-1.
 *
 * <ul>
 *   <li>{@code provider} = {@code mock} (default, W10-1) | {@code insung-quick} (W10-2 통합 시점)</li>
 *   <li>{@code insungQuick.*} — 인성데이타 vendor 시크릿 (W10-2 활성)</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "samhan.arologis.matcher")
public class ArologisMatcherProperties {

    private String provider = "mock";
    private InsungQuick insungQuick = new InsungQuick();

    @Data
    public static class InsungQuick {
        private String apiUrl;
        private String apiKey;
        private String partnerId;
    }
}
