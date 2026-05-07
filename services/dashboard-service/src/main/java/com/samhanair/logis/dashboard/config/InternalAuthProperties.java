package com.samhanair.logis.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내부 호출자 (운영 admin 도구 / 형제 service) 가 dashboard-service 의 {@code /internal/**}
 * endpoint 호출 시 제출하는 X-Internal-Token 의 expected 값.
 *
 * <p>Phase 8 2차 표준 — {@code app.security.internal.token} property,
 * {@code SAMHAN_INTERNAL_TOKEN} 환경변수 우선, {@code INTERNAL_AUTH_TOKEN} legacy fallback.
 *
 * <p>prod 프로파일에서 dev 기본값 사용 시 {@link InternalTokenGuard} 가 부팅을 거부.
 */
@Data
@ConfigurationProperties(prefix = "app.security.internal")
public class InternalAuthProperties {

    private String token;
}
