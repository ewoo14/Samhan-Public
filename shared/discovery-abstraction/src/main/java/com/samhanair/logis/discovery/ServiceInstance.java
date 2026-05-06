package com.samhanair.logis.discovery;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Service discovery lookup 결과 record.
 *
 * <p>Eureka 의 {@code com.netflix.appinfo.InstanceInfo} 또는 AWS Cloud Map 의
 * {@code HttpInstanceSummary} 등 vendor-specific 객체를 본 record 로 정규화하여
 * 호출 측이 vendor 차이에 의존하지 않도록 한다.
 *
 * <p>불변 (immutable) — 모든 필드 final, metadata map 은 조회 시점에 unmodifiable wrap.
 *
 * @param serviceName Eureka VIP / Cloud Map service name (예: {@code product-service})
 * @param host        instance host (DNS 또는 IP)
 * @param port        instance port
 * @param secure      HTTPS 여부
 * @param metadata    vendor-specific metadata (예: zone, region, instance-id)
 */
public record ServiceInstance(
        String serviceName,
        String host,
        int port,
        boolean secure,
        Map<String, String> metadata) {

    public ServiceInstance {
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        metadata = (metadata == null) ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }
}
