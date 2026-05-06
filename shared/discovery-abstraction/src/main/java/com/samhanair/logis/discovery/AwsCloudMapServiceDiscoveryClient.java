package com.samhanair.logis.discovery;

import java.util.List;

/**
 * {@link ServiceDiscoveryClient} 의 AWS Cloud Map placeholder 구현체.
 *
 * <p>본 클래스는 Phase 10 AWS cutover 시점에 실제 구현이 들어올 자리를 표시한다.
 * 현재 시점 (Phase 8 2차) 에서는 모든 메서드가
 * {@link UnsupportedOperationException} 을 throw 한다.
 *
 * <p>Phase 10 시점 구현 안내:
 * <ul>
 *   <li>의존성 = {@code software.amazon.awssdk:servicediscovery} (AWS SDK v2)</li>
 *   <li>register = {@code RegisterInstanceRequest} (DnsName, Port, custom attribute)</li>
 *   <li>deregister = {@code DeregisterInstanceRequest}</li>
 *   <li>lookup = {@code DiscoverInstancesRequest} (NamespaceName + ServiceName)</li>
 *   <li>healthcheck = {@code GetInstancesHealthStatusRequest} 또는 lookup 결과 비어 있지 않은지</li>
 * </ul>
 *
 * <p>본 placeholder 의 존재 자체가 호환성 가드 — Phase 8 2차 시점에 interface 가 있어야
 * 14 service 의 호출 측 코드가 vendor-agnostic 으로 진화할 수 있다.
 *
 * @see ServiceDiscoveryClient
 * @see EurekaServiceDiscoveryClient 현재 운영 impl
 */
public class AwsCloudMapServiceDiscoveryClient implements ServiceDiscoveryClient {

    private static final String UNSUPPORTED_MESSAGE =
            "Phase 10 cutover 시점 구현 — AWS Cloud Map 추상화 placeholder";

    @Override
    public void register(String serviceName, String host, int port) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public void deregister(String serviceName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public List<ServiceInstance> lookup(String serviceName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean healthcheck(String serviceName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }
}
