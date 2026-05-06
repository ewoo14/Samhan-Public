package com.samhanair.logis.groupware;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.groupware.config.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Groupware Service entry point — Phase 9 W2.
 *
 * <p>결재선 + 메신저 + 일정 도메인의 단일 진입점. 3 entity (ApprovalLine + Message + Schedule)
 * + 2 부속 entity (ApprovalStep + ScheduleParticipant) + 3 enum (ApprovalStatus / MessageStatus
 * / ScheduleStatus) + 2 controller (Internal / Admin) + 3 service.
 *
 * <p>외부 의존성 — UserClient (user-service Internal API). ServiceDiscoveryClient 두 번째
 * 소비자 (W1 partner-service 첫 소비자, M-PHASE-9-readiness §6 일관).
 */
@SpringBootApplication
@EnableDiscoveryClient
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class GroupwareServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupwareServiceApplication.class, args);
    }
}
