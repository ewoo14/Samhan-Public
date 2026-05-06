package com.samhanair.logis.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Materialized view REFRESH scheduling 설정 — Phase 9 W4 (D-P9-13).
 *
 * <p>{@code intervalMinutes} — REFRESH 주기 (분 단위, default 5). admin trigger 도 가능.
 */
@Data
@ConfigurationProperties(prefix = "samhan.dashboard.refresh")
public class DashboardRefreshProperties {

    private int intervalMinutes = 5;
}
