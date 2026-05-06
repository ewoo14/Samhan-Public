package com.samhanair.logis.partner;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.partner.config.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Partner Service entry point — Phase 9 W1.
 *
 * <p>거래처 마스터 도메인의 단일 진입점. 2 entity (Partner / PartnerCreditHistory) +
 * 2 controller (Internal lookup / Admin CRUD) + 2 service (PartnerService /
 * PartnerCreditService) 활성화.
 *
 * <p>본 service 도입의 1차 목적은 slip-service M5 가 partnerCode → partnerId lookup 을
 * 외부 호출 없이 처리할 수 있도록 {@code GET /internal/partners/{partnerCode}} endpoint 를
 * 제공하는 것 (M-PHASE-9-readiness §2-3 참조).
 *
 * <p>Phase 8 2차 정착 — {@code shared:discovery-abstraction} 의존성으로 ServiceDiscoveryClient
 * provider toggle 보유 (Phase 10 cutover 시점 활성).
 */
@SpringBootApplication
@EnableDiscoveryClient
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class PartnerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnerServiceApplication.class, args);
    }
}
