package com.samhanair.logis.notification.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 10 PR-F1 BE-1 — {@link AligoCsvSourceClient} 의 fail-soft default placeholder.
 *
 * <p>{@link RestClientAligoCsvSourceClient} 가 등록 가능한 모든 환경에서는 자동 비활성. test 시점에는
 * IT 가 {@code AligoCsvSourceClient} 를 직접 사용하지 않으므로 본 placeholder 가
 * {@link com.samhanair.logis.notification.service.AligoAddressBookSyncService} bean 생성 시
 * dependency 충족만 보장한다.
 *
 * <p>설계 — {@link NoopPartnerLookupClient} / {@link MockAligoAddressBookClient} 와 동일 패턴
 * ({@code @Configuration} + {@code @Bean} + {@code @ConditionalOnMissingBean}).
 *
 * <p>본 placeholder 는 항상 빈 리스트 반환 — 운영자가 잘못된 환경 배치 시 sync 결과 응답에
 * "fetch 결과 비어있음" WARN 로그가 즉시 노출된다 (silent success 회피).
 *
 * <h2>종합 fix — bean name 충돌 회피 (PR #119 CI run 25615955037 회귀)</h2>
 * <p>이전 fix ({@code @Profile("!test")}) 는 IT 가 active profile 미명시 → 무효였다. 본 종합 fix —
 * {@code @Bean} 메서드 이름에 {@code Bean} suffix 추가 ({@code noopAligoCsvSourceClient} →
 * {@code noopAligoCsvSourceClientBean}) 로 클래스 빈 이름 ({@code noopAligoCsvSourceClient}) 과
 * 메서드 빈 이름 충돌 원천 회피.
 *
 * <p>(memory feedback_it_mockbean_external_clients — IT 외부 client @MockBean 격리 패턴 일관)
 */
@Configuration
public class NoopAligoCsvSourceClient {

    private static final Logger log = LoggerFactory.getLogger(NoopAligoCsvSourceClient.class);

    @Bean
    @ConditionalOnMissingBean(AligoCsvSourceClient.class)
    public AligoCsvSourceClient noopAligoCsvSourceClientBean() {
        log.warn("AligoCsvSourceClient 실 구현체 미등록 — Noop placeholder 활성 (test profile 또는 프로퍼티 비활성).");
        return List::of;
    }
}
