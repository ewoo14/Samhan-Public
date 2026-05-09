package com.samhanair.logis.notification.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@link PartnerLookupClient} 의 fail-soft 기본 구현 — BE-E 의 by-name endpoint 머지 전까지의 placeholder.
 *
 * <p>{@link ConditionalOnMissingBean} 가드로 실제 RestClient 기반 구현체가 등록되면 본 빈은 자동 비활성화.
 * 본 placeholder 는 항상 {@link Optional#empty()} 반환 — import 시도 시 모든 row 가 reject 로 누적되어
 * 운영자가 BE-E 의존성 미충족을 즉시 인지할 수 있도록 한다 (silent success 방지).
 *
 * <p>본 PR-D Part 2-3 단독 build/test 통과를 위해 존재. 실 production 운영 시점에는 RestClient impl 필수.
 *
 * <h2>설계 — TM PR #115 회귀 fix 후속 (PR-D BE 회귀 fix)</h2>
 * <p>이전 fix 시도 ({@code @Component} + {@code implements PartnerLookupClient} +
 * {@code @ConditionalOnMissingBean(PartnerLookupClient.class)}) 는 component scan 단계에서
 * conditional 평가 시 자기 자신이 {@link PartnerLookupClient} 후보로 카운트되어 condition 이
 * false 가 되어 등록 자체가 skip 되었다 (CI run 25605160833 — NotificationAdminControllerIT /
 * NotificationInternalControllerIT 10건 NoSuchBeanDefinitionException).
 *
 * <p>안정 패턴인 {@code @Configuration} + {@code @Bean} 메서드 + 메서드 레벨 {@code @ConditionalOnMissingBean}
 * 으로 복원. {@code @Bean} 메서드 레벨 conditional 은 자기 자신을 카운트하지 않으며, 실 RestClient 구현체
 * 등록 시 이 noop bean 이 자동 비활성화된다. {@code @MockBean} 사용 IT 는 mock 이 우선 등록되어
 * conditional 이 false 평가 → noop bean skip (BeanDefinitionOverrideException 회피).
 *
 * <h2>3차 fix — {@code @Profile("!test")} 분리 (PR #115 CI run 25605365243 회귀)</h2>
 * <p>fix #1 (553d201) {@code @Configuration} + {@code @Bean} 복원 → BeanDefinitionOverrideException 15건.
 * fix #2 (e63a6a3) test resources {@code spring.main.allow-bean-definition-overriding=true} 추가 →
 * BeanDefinitionStoreException 동일 15건 (override 허용해도 {@code @MockBean} 의 동일 type 두 번째 등록
 * 충돌 회피 실패). 본 3차 fix 는 단순화 — test profile 에서 본 noop {@code @Configuration} 자체를
 * 비활성화하여 {@code @MockBean PartnerLookupClient} 가 단독 등록되도록 한다.
 * production 영향 0 (test 외 모든 profile 에서 활성).
 *
 * <p>(memory feedback_it_mockbean_external_clients — IT 외부 client @MockBean 격리 패턴 일관)
 */
@Configuration
@Profile("!test")
public class NoopPartnerLookupClient {

    private static final Logger log = LoggerFactory.getLogger(NoopPartnerLookupClient.class);

    /**
     * Noop fail-soft {@link PartnerLookupClient} placeholder bean.
     *
     * <p>{@link ConditionalOnMissingBean} 평가 시점에 본 메서드는 후보로 카운트되지 않으므로
     * (Spring Boot 표준 동작), 실 RestClient impl 또는 {@code @MockBean} 이 등록되면 자동 비활성화.
     */
    @Bean
    @ConditionalOnMissingBean(PartnerLookupClient.class)
    public PartnerLookupClient noopPartnerLookupClient() {
        log.warn("PartnerLookupClient 실 구현체 미등록 — Noop placeholder 활성. "
                + "BE-E 의 GET /api/v1/partners/by-name + GET /api/v1/partners/{partnerCode} "
                + "endpoint 머지 후 RestClient impl 등록 필요.");
        return new PartnerLookupClient() {
            @Override
            public Optional<String> findPartnerCodeByName(String businessName) {
                return Optional.empty();
            }

            @Override
            public Optional<String> verifyPartnerCode(String partnerCode) {
                return Optional.empty();
            }
        };
    }
}
