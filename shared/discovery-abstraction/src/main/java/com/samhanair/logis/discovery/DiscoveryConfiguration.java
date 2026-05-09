package com.samhanair.logis.discovery;

import com.netflix.discovery.EurekaClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

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
 * <p>Phase 9 W2~W4 회고 — 본 클래스를 {@link AutoConfiguration} 으로 승격 +
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 등록. 신규 service (groupware/notification/dashboard) 가 별도 {@code @Import}
 * 없이도 ServiceDiscoveryClient bean 을 자동 주입받는다.
 *
 * <p><b>PR #105 회고 fix</b>: 직전 PR #104 의 {@code @ConditionalOnBean(EurekaClient.class)}
 * 가드는 Spring Boot 의 조기 평가 단계에서 EurekaClient bean 미생성 (lazy init) 으로 판정 →
 * autoconfig 활성 안 됨 → 정상 startup (eureka.client.enabled=true) 환경에서도 회귀 fail.
 * 본 fix — {@code @ConditionalOnProperty(eureka.client.enabled, matchIfMissing=true)} 패턴 채택:
 * - 정상 startup (eureka.client.enabled 미설정 default true 또는 명시 true): 활성 → bean 등록
 * - IT (eureka.client.enabled=false 명시): 비활성 → bean 미등록 → @MockBean UserClient 패턴 동작
 */
@AutoConfiguration
public class DiscoveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(ServiceDiscoveryClient.class)
    @ConditionalOnClass(name = "com.netflix.discovery.EurekaClient")
    @ConditionalOnExpression("${eureka.client.enabled:true} and '${samhan.discovery.provider:eureka}'.equals('eureka')")
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
