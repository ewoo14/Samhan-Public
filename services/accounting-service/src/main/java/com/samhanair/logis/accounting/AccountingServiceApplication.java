package com.samhanair.logis.accounting;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Accounting Service entry point — Phase 4 Slice A (A1+A2 통합 MVP).
 *
 * <p>한국 일반기업회계기준 표준 계정과목 시드 + 분개장 (Journal/JournalLine) + 시산표 service.
 * Slice A 미포함: A3 slip 자동 분개 (RestClient), A4 AR/AP.
 */
@SpringBootApplication
@EnableDiscoveryClient
@Import(JpaAuditingConfig.class)
public class AccountingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingServiceApplication.class, args);
    }
}
