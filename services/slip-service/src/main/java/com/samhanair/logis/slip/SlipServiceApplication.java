package com.samhanair.logis.slip;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Slip Service entry point — Slip(STI) + SlipLine + SlipNumberSequence (plan §3.1 first slice).
 *
 * <p>{@code @EnableScheduling} — PR-H1 (Phase 12 Step 1) {@link
 * com.samhanair.logis.slip.realtime.SlipRealtimeBroker#heartbeat()} 30s @Scheduled 활성.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@Import(JpaAuditingConfig.class)
public class SlipServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlipServiceApplication.class, args);
    }
}
