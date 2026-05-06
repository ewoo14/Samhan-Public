package com.samhanair.logis.discovery;

import java.util.List;

/**
 * Service discovery 추상화 interface.
 *
 * <p>현재 운영 (Eureka) 와 향후 마이그레이션 후보 (AWS Cloud Map) 사이의 vendor 차이를
 * 호출 측 코드로부터 격리한다. 본 interface 의 의도는 Phase 10 AWS cutover 시점에
 * 소비 service 의 build.gradle 한 줄 + application.yml 한 줄 만으로 vendor 전환을
 * 완료하는 것이다.
 *
 * <p>4 operation:
 * <ol>
 *   <li>{@link #register(String, String, int)} — instance 자체 등록 (보통 framework 가 자동 수행, 본 호출은 dynamic registration 한정)</li>
 *   <li>{@link #deregister(String)} — instance 자체 등록 해제 (graceful shutdown 시점)</li>
 *   <li>{@link #lookup(String)} — service name 으로 인스턴스 목록 조회</li>
 *   <li>{@link #healthcheck(String)} — service name 단일 healthcheck (lookup 결과 비어 있지 않은지)</li>
 * </ol>
 *
 * <p>Phase 8 2차 시점 = wrapper 작성만, 실 service 의존성 추가는 Phase 10 cutover 시점.
 *
 * @see EurekaServiceDiscoveryClient 현재 운영 impl
 * @see AwsCloudMapServiceDiscoveryClient Phase 10 cutover 시점 예정 impl (placeholder)
 */
public interface ServiceDiscoveryClient {

    /**
     * 자기 자신 instance 등록 (보통 Spring Cloud auto-registration 으로 충분, 본 호출은
     * dynamic / programmatic registration 한정).
     *
     * @param serviceName 등록 service 명 (예: {@code product-service})
     * @param host        등록 host
     * @param port        등록 port
     */
    void register(String serviceName, String host, int port);

    /**
     * 자기 자신 instance 등록 해제 (graceful shutdown 시점).
     *
     * @param serviceName 해제 service 명
     */
    void deregister(String serviceName);

    /**
     * service name 으로 인스턴스 목록 조회.
     *
     * @param serviceName 조회 service 명 (예: {@code product-service})
     * @return 활성 인스턴스 목록 (없으면 빈 list)
     */
    List<ServiceInstance> lookup(String serviceName);

    /**
     * service name 단일 healthcheck (lookup 결과 비어 있지 않은지).
     *
     * @param serviceName 검사 service 명
     * @return 1개 이상 활성 인스턴스 존재 시 {@code true}
     */
    boolean healthcheck(String serviceName);
}
