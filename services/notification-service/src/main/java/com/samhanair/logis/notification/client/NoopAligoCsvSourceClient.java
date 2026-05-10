package com.samhanair.logis.notification.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Phase 10 PR-F1 BE-1 — {@link AligoCsvSourceClient} 의 fail-soft default placeholder.
 *
 * <p>{@link RestClientAligoCsvSourceClient} 가 등록 가능한 모든 환경에서는 자동 비활성. test profile
 * 에서는 {@link RestClientAligoCsvSourceClient} 가 {@code @Profile("!test")} 로 인해 등록되지
 * 않으므로 본 fail-soft default 가 활성화되어 {@link com.samhanair.logis.notification.service.AligoAddressBookSyncService}
 * bean 생성 시 inject 누락 회귀를 방지한다 ({@code @MockBean AligoCsvSourceClient} 등록 시 본 빈
 * 자동 비활성화).
 *
 * <p>설계 — {@link NoopPartnerLookupClient} / {@link MockAligoAddressBookClient} 와 동일 패턴
 * ({@code @Configuration} + {@code @Bean} + {@code @ConditionalOnMissingBean} + {@code @Profile("!test")}).
 *
 * <p>본 placeholder 는 항상 빈 리스트 반환 — 운영자가 잘못된 환경 배치 시 sync 결과 응답에
 * "fetch 결과 비어있음" WARN 로그가 즉시 노출된다 (silent success 회피).
 *
 * <h2>{@code @Profile("!test")} 가드 — PR #119 회귀 fix (PR #115 NoopPartnerLookupClient fix 패턴 일관)</h2>
 * <p>test profile 에서 본 {@code @Configuration} 자체를 비활성화하여 {@code @MockBean AligoCsvSourceClient}
 * 가 단독 등록되도록 한다. {@code @ConditionalOnMissingBean} + {@code @MockBean} 조합 단독으로는
 * BeanDefinitionOverrideException 회피 불가 (PR #115 1차/2차 fix 회고). production 영향 0
 * (test 외 모든 profile 에서 활성).
 *
 * <p>(memory feedback_it_mockbean_external_clients — IT 외부 client @MockBean 격리 패턴 일관)
 */
@Configuration
@Profile("!test")
public class NoopAligoCsvSourceClient {

    private static final Logger log = LoggerFactory.getLogger(NoopAligoCsvSourceClient.class);

    @Bean
    @ConditionalOnMissingBean(AligoCsvSourceClient.class)
    public AligoCsvSourceClient noopAligoCsvSourceClient() {
        log.warn("AligoCsvSourceClient 실 구현체 미등록 — Noop placeholder 활성 (test profile 또는 프로퍼티 비활성).");
        return List::of;
    }
}
