package com.samhanair.logis.slip;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.slip.config.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/** Slip Service entry point — Slip(STI) + SlipLine + SlipNumberSequence (plan §3.1 first slice). */
@SpringBootApplication
@EnableDiscoveryClient
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class SlipServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlipServiceApplication.class, args);
    }
}
