package com.samhanair.logis.partnerauth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dc-config-service (M3) 호출 설정 ({@code samhan.dc-config.*}).
 *
 * <p>현 PR 단계 (M2 W2) 는 직접 base URL 을 사용. W3 정식 구현 시점에
 * Eureka {@code lb://dc-config-service} 로 전환 예정.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "samhan.dc-config")
public class DcConfigClientProperties {

    /** dc-config-service base URL (예: http://dc-config-service:8089). */
    private String url = "http://dc-config-service:8089";

    /** 호출 타임아웃 (ms). */
    private int timeoutMs = 3000;
}
