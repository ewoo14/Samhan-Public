package com.samhanair.logis.arologis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내부 호출자 (운영 admin 도구 / 형제 service / 외부 vendor callback) 가 arologis-service 의
 * {@code /internal/**} endpoint 호출 시 제출하는 X-Internal-Token 의 expected 값.
 *
 * <p>Phase 10 W10-1 — {@code app.security.internal.token} property,
 * {@code SAMHAN_INTERNAL_TOKEN} 환경변수 (chained-default 표준).
 *
 * <p>prod 프로파일에서 dev 기본값 사용 시 {@link InternalTokenGuard} 가 부팅을 거부.
 */
@Data
@ConfigurationProperties(prefix = "app.security.internal")
public class InternalAuthProperties {

    private String token;
}
