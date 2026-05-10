package com.samhanair.logis.dcconfig.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import com.samhanair.logis.shared.realtime.broker.RealtimePublishHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * dc-config-service SSE broker — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link InMemoryRealtimeBroker} thin facade. 도메인별 event name 명시 + bean 등록.
 * slip-service 의 SlipRealtimeBroker 패턴 1:1 복제.
 *
 * <p><b>SSE event 표준</b>:
 * <ul>
 *   <li>{@code "dc-config:edit"} — DcConfig 변경 broadcast</li>
 *   <li>{@code "dc-config:edit-request:created"} — 수정 요청 생성</li>
 *   <li>{@code "dc-config:edit-request:decided"} — 수락/거절/만료</li>
 * </ul>
 */
public class DcConfigRealtimeBroker extends InMemoryRealtimeBroker {

    public static final String EVENT_EDIT = "dc-config:edit";
    public static final String EVENT_REQUEST_CREATED = "dc-config:edit-request:created";
    public static final String EVENT_REQUEST_DECIDED = "dc-config:edit-request:decided";

    @Override
    @Autowired(required = false)
    public void setPublishHook(RealtimePublishHook hook) {
        super.setPublishHook(hook);
    }

    @Configuration
    public static class DcConfigRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(DcConfigRealtimeBroker.class)
        public DcConfigRealtimeBroker dcConfigRealtimeBrokerBean() {
            return new DcConfigRealtimeBroker();
        }
    }
}
