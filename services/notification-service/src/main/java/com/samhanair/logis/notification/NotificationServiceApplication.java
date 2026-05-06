package com.samhanair.logis.notification;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.notification.config.AligoProperties;
import com.samhanair.logis.notification.config.FcmProperties;
import com.samhanair.logis.notification.config.InternalAuthProperties;
import com.samhanair.logis.notification.config.UserCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Notification Service entry point — Phase 9 W3.
 *
 * <p>푸시 / 이메일 / SMS 통합 라우터의 단일 진입점. 2 entity (NotificationRequest +
 * NotificationLog) + 3 enum (NotificationChannel / NotificationStatus / RecipientType)
 * + 3 channel adapter (FcmPush / SesEmail / AligoSms) + 2 controller (Internal / Admin) + 1 service.
 *
 * <p>외부 의존성 — UserClient (user-service Internal API). ServiceDiscoveryClient 세 번째
 * 소비자 (W1 partner / W2 groupware → W3 notification, M-PHASE-9-readiness §6 일관).
 *
 * <p>Phase 9 W3 — UserClient bulk verify endpoint + Caffeine cache (TTL 60초) 도입 (BE backlog #4 채택).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties({
        InternalAuthProperties.class,
        AligoProperties.class,
        FcmProperties.class,
        UserCacheProperties.class
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
