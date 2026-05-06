package com.samhanair.logis.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServiceDiscoveryClient} 의 Eureka 구현체. 현재 운영 환경 (카페24 + Render)
 * 에서 활성. Phase 10 AWS cutover 시점에 {@link AwsCloudMapServiceDiscoveryClient}
 * 로 교체 예정.
 *
 * <p>{@link EurekaClient} 가 framework 차원 auto-registration / heartbeat /
 * deregistration 을 모두 처리하므로 {@link #register} / {@link #deregister} 는 보통
 * no-op (호출 측이 dynamic registration 을 명시적으로 요구한 경우만 사용).
 *
 * <p>{@link #lookup} 은 {@code EurekaClient.getApplication(serviceName)} 결과를
 * {@link ServiceInstance} list 로 정규화한다.
 *
 * @see ServiceDiscoveryClient
 */
@Slf4j
@RequiredArgsConstructor
public class EurekaServiceDiscoveryClient implements ServiceDiscoveryClient {

    private final EurekaClient eurekaClient;

    @Override
    public void register(String serviceName, String host, int port) {
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(host, "host");
        // Eureka 의 자체 registration 은 EurekaClient 부팅 시 자동 처리.
        // 본 메서드는 dynamic registration 호출 호환을 위한 logging only no-op.
        log.debug("[discovery] eureka register no-op (auto-handled by EurekaClient): "
                + "service={}, host={}, port={}", serviceName, host, port);
    }

    @Override
    public void deregister(String serviceName) {
        Objects.requireNonNull(serviceName, "serviceName");
        // Eureka 의 deregistration 은 EurekaClient.shutdown() 시점 자동 처리.
        // 본 메서드는 graceful-shutdown 호출 호환을 위한 logging only no-op.
        log.debug("[discovery] eureka deregister no-op (auto-handled by EurekaClient.shutdown): "
                + "service={}", serviceName);
    }

    @Override
    public List<ServiceInstance> lookup(String serviceName) {
        Objects.requireNonNull(serviceName, "serviceName");
        var application = eurekaClient.getApplication(serviceName);
        if (application == null) {
            return List.of();
        }
        return application.getInstances().stream()
                .filter(i -> i.getStatus() == InstanceInfo.InstanceStatus.UP)
                .map(EurekaServiceDiscoveryClient::toServiceInstance)
                .toList();
    }

    @Override
    public boolean healthcheck(String serviceName) {
        return !lookup(serviceName).isEmpty();
    }

    private static ServiceInstance toServiceInstance(InstanceInfo info) {
        Map<String, String> metadata = new HashMap<>();
        if (info.getMetadata() != null) {
            metadata.putAll(info.getMetadata());
        }
        metadata.put("instanceId", info.getInstanceId());
        boolean secure = info.isPortEnabled(InstanceInfo.PortType.SECURE);
        int port = secure ? info.getSecurePort() : info.getPort();
        return new ServiceInstance(
                info.getAppName(),
                info.getHostName(),
                port,
                secure,
                metadata);
    }
}
