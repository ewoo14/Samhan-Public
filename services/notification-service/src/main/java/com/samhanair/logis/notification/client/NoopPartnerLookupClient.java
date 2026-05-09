package com.samhanair.logis.notification.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * {@link PartnerLookupClient} 의 fail-soft 기본 구현 — BE-E 의 by-name endpoint 머지 전까지의 placeholder.
 *
 * <p>본 PR-D Part 2-3 단독 build/test 통과를 위해 존재. 실 production 운영 시점에는 RestClient impl 필수.
 *
 * <h2>설계 — TM PR #115 fix (15 IT BeanDefinitionOverrideException 회귀)</h2>
 * <p>이전 형태 ({@code @Configuration} + {@code @Bean} + {@code @ConditionalOnMissingBean})
 * 는 {@code @MockBean PartnerLookupClient} 사용 IT 에서 {@code BeanDefinitionOverrideException}
 * 을 발생시켰다. 원인: {@code @Configuration} 의 {@code @Bean} 메서드는 component scan 단계에서
 * {@code @ConditionalOnMissingBean} 평가가 {@code @MockBean} 의 mock bean 등록보다 늦게
 * 평가되어 noop bean 과 mock bean 이 동시에 등록 시도된다.
 *
 * <p>대신 본 클래스를 {@code @Component} + {@code PartnerLookupClient} 직접 구현으로 변경.
 * Spring Boot 의 component scan 단계에서 {@code @ConditionalOnMissingBean(PartnerLookupClient.class)}
 * 가 안정적으로 평가되어 {@code @MockBean} 우선 등록 시 본 noop component 자체가 등록되지 않는다.
 * (memory feedback_it_mockbean_external_clients — IT 외부 client @MockBean 격리 패턴)
 */
@Component
@ConditionalOnMissingBean(PartnerLookupClient.class)
public class NoopPartnerLookupClient implements PartnerLookupClient {

    private static final Logger log = LoggerFactory.getLogger(NoopPartnerLookupClient.class);

    public NoopPartnerLookupClient() {
        log.warn("PartnerLookupClient 실 구현체 미등록 — Noop placeholder 활성. "
                + "BE-E 의 GET /api/v1/partners/by-name + GET /api/v1/partners/{partnerCode} "
                + "endpoint 머지 후 RestClient impl 등록 필요.");
    }

    @Override
    public Optional<String> findPartnerCodeByName(String businessName) {
        return Optional.empty();
    }

    @Override
    public Optional<String> verifyPartnerCode(String partnerCode) {
        return Optional.empty();
    }
}
