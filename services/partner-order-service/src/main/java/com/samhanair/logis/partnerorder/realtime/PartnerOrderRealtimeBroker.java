package com.samhanair.logis.partnerorder.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 거래처 주문 실시간 SSE 브로커 — PR-H4b (Phase 12 Step 4b BE-C) thin facade.
 *
 * <p>{@link InMemoryRealtimeBroker} ({@code shared:realtime-abstraction}) 를 그대로 상속하여
 * 모든 메서드 시그니처 동일. partner-order-service 내부 호출자가 명시 reference 사용 시 Type
 * binding 가독성 향상.
 *
 * <p>RedisRealtimeBroker (cross-node 전파) 는 {@code samhan.realtime.broker=redis} 시점만
 * 자동 활성 (shared module BrokerConfiguration). 본 facade 의 hook setter 메커니즘은 동일
 * 동작.
 *
 * <p><b>Bean override 패턴</b>: shared:realtime-abstraction 의 default RealtimeBroker bean 을
 * 본 facade (subclass) 로 override. {@code @ConditionalOnMissingBean(PartnerOrderRealtimeBroker.class)}
 * 명시.
 */
public class PartnerOrderRealtimeBroker extends InMemoryRealtimeBroker {

    /**
     * RealtimePublishHook bean 등록 시 자동 setter 주입 (cross-node Redis 환경). bean 미등록 시
     * Optional.empty 유지.
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
     * PartnerOrderRealtimeBroker bean 등록 — partner-order-service local @Configuration.
     *
     * <p><b>*Bean suffix 가드</b>: bean method name {@code partnerOrderRealtimeBrokerBean}.
     */
    @Configuration
    public static class PartnerOrderRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(PartnerOrderRealtimeBroker.class)
        public PartnerOrderRealtimeBroker partnerOrderRealtimeBrokerBean() {
            return new PartnerOrderRealtimeBroker();
        }
    }
}
