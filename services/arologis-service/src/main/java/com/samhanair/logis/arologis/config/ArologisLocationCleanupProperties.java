package com.samhanair.logis.arologis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DriverLocation 30일 cleanup 설정 — Phase 10 W10-1.
 *
 * <ul>
 *   <li>{@code retentionDays} — GPS 데이터 보존 일수 (default 30)</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "samhan.arologis.location-cleanup")
public class ArologisLocationCleanupProperties {

    private int retentionDays = 30;
}
