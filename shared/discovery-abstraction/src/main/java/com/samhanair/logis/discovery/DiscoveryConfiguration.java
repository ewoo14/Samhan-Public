package com.samhanair.logis.discovery;

import com.netflix.discovery.EurekaClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link ServiceDiscoveryClient} bean 자동 구성.
 *
 * <p>{@code samhan.discovery.provider} property 로 활성 impl 을 토글한다:
 * <ul>
 *   <li>{@code eureka} (default, matchIfMissing) — {@link EurekaServiceDiscoveryClient}</li>
 *   <li>{@code aws-cloud-map} — {@link AwsCloudMapServiceDiscoveryClient} (Phase 10 cutover 시점 활성)</li>
 * </ul>
 *
 * <p>Eureka bean 은 {@link EurekaClient} 가 classpath + bean context 에 존재할 때만
 * 등록한다 (소비자가 spring-cloud-starter-netflix-eureka-client 의존성을 명시 추가한
 * 경우). 그렇지 않으면 자동 구성 자체가 스킵되어 본 모듈을 단순 의존하는 것이
 * Eureka 동작을 강제하지 않는다.
 *
 * <p>본 구성은 Phase 8 2차 시점 = 컴파일 가능성 + 단위 테스트 가능성 보장만, 14 service
 * 의 build.gradle 의존성 추가는 Phase 10 cutover 시점 위임.
 */
@Configuration(proxyBeanMethods = false)
public class DiscoveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(ServiceDiscoveryClient.class)
    @ConditionalOnClass(name = "com.netflix.discovery.EurekaClient")
    @ConditionalOnProperty(name = "samhan.discovery.provider", havingValue = "eureka", matchIfMissing = true)
    public ServiceDiscoveryClient eurekaServiceDiscoveryClient(EurekaClient eurekaClient) {
        return new EurekaServiceDiscoveryClient(eurekaClient);
    }

    @Bean
    @ConditionalOnMissingBean(ServiceDiscoveryClient.class)
    @ConditionalOnProperty(name = "samhan.discovery.provider", havingValue = "aws-cloud-map")
    public ServiceDiscoveryClient awsCloudMapServiceDiscoveryClient() {
        return new AwsCloudMapServiceDiscoveryClient();
    }
}
