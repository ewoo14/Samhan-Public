package com.samhanair.logis.product;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.product.config.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Product Service entry point — Product + Category aggregate (plan §3.5 first slice).
 *
 * <p>{@link EnableScheduling} — 옵션 C-2 (시트 → DB cron 1시간 주기 sync, 개발책임자 결정 2026-05-05).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
