package com.samhanair.logis.slip.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 슬립 실시간 SSE 브로커 — PR-H4a (Phase 12 Step 4a) 마이그 thin facade.
 *
 * <p><b>본 PR (PR-H4a)</b>: 기존 in-memory broker 구현은 {@link InMemoryRealtimeBroker}
 * (shared:realtime-abstraction) 로 이동. 본 클래스는 호환성 유지 (기존 호출자 0 변경) 위한
 * 얇은 facade — InMemoryRealtimeBroker 를 그대로 상속하여 모든 메서드 시그니처 동일.
 *
 * <p>RedisRealtimeBroker (slip-service local) 도 마찬가지로 {@code shared:realtime-abstraction}
 * 의 {@link com.samhanair.logis.shared.realtime.broker.RedisRealtimeBroker} 로 이동되었으며,
 * {@code app.realtime.broker=redis} 시점만 자동 활성. slip-service 의 기존 hook setter 메커니즘은
 * 동일 동작 ({@code @Autowired(required=false)} setPublishHook).
 *
 * <p><b>마이그 결정 근거</b>: slip-service 는 14 service 의 시범 활용 사례. 다른 13 service 는
 * PR-H4b 에서 자체 facade 없이 직접 InMemoryRealtimeBroker 를 활용 (slip-service 만 기존
 * SlipRealtimeBroker 명시 reference 가 다수라 thin facade 보존).
 *
 * <p><b>회귀 가드</b>: 본 PR 의 모든 기존 단위/IT 가 변경 없이 PASS — 메서드 시그니처 + 동작
 * 100% 일관.
 */
public class SlipRealtimeBroker extends InMemoryRealtimeBroker {

    /**
     * Spring 이 RealtimePublishHook bean 등록 시 자동 setter 주입 (@Autowired(required=false)
     * 의미는 InMemoryRealtimeBroker 와 동일). RedisRealtimeBroker bean 미등록 (default 단일 노드)
     * 환경에서는 hook 미설정.
     *
     * <p>본 override 는 단순 super delegate. {@code @Autowired(required=false)} 가 자식 클래스에서
     * 명시적으로 재선언되어야 Spring DI 가 본 facade bean 에 주입.
     */
    @Override
    @Autowired(required = false)
    public void setPublishHook(
            com.samhanair.logis.shared.realtime.broker.RealtimePublishHook hook) {
        super.setPublishHook(hook);
    }

    /**
     * SlipRealtimeBroker bean 등록 — slip-service local @Configuration. shared module 의 default
     * RealtimeBroker bean 을 SlipRealtimeBroker (subclass) 로 override.
     *
     * <p>{@code @ConditionalOnMissingBean(SlipRealtimeBroker.class)} — 명시 override 보장.
     * shared 의 BrokerConfiguration 도 {@code @ConditionalOnMissingBean(RealtimeBroker.class)}
     * 라 본 facade 가 RealtimeBroker bean 자리를 차지함 → shared 의 InMemoryRealtimeBroker bean
     * 미등록.
     *
     * <p><b>*Bean suffix 가드</b>: bean method name {@code slipRealtimeBrokerBean} (suffix 명시).
     */
    @Configuration
    public static class SlipRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(SlipRealtimeBroker.class)
        public SlipRealtimeBroker slipRealtimeBrokerBean() {
            return new SlipRealtimeBroker();
        }
    }
}
