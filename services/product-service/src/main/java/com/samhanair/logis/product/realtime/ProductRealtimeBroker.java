package com.samhanair.logis.product.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 제품 마스터 실시간 SSE 브로커 — PR-H4b (Phase 12 Step 4b BE-C) thin facade.
 *
 * <p>{@link InMemoryRealtimeBroker} ({@code shared:realtime-abstraction}) 를 그대로 상속.
 * 메서드 시그니처 동일.
 */
public class ProductRealtimeBroker extends InMemoryRealtimeBroker {

    @Override
    @Autowired(required = false)
    public void setPublishHook(
            com.samhanair.logis.shared.realtime.broker.RealtimePublishHook hook) {
        super.setPublishHook(hook);
    }

    /**
     * ProductRealtimeBroker bean 등록 — product-service local @Configuration.
     *
     * <p><b>*Bean suffix 가드</b>: bean method name {@code productRealtimeBrokerBean}.
     */
    @Configuration
    public static class ProductRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(ProductRealtimeBroker.class)
        public ProductRealtimeBroker productRealtimeBrokerBean() {
            return new ProductRealtimeBroker();
        }
    }
}
