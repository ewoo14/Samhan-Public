package com.samhanair.logis.user.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import com.samhanair.logis.shared.realtime.broker.RealtimePublishHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service SSE broker — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link InMemoryRealtimeBroker} 를 thin facade 로 상속 — 도메인별 event name 명시 + bean
 * 등록. slip-service 의 SlipRealtimeBroker 패턴 1:1 복제.
 *
 * <p><b>SSE event 표준</b>:
 * <ul>
 *   <li>{@code "user:edit"} — Employee/Department 변경 broadcast</li>
 *   <li>{@code "user:edit-request:created"} — DEACTIVATED 직원 수정 요청 생성</li>
 *   <li>{@code "user:edit-request:decided"} — 수락/거절/만료</li>
 * </ul>
 *
 * <p>Redis cross-node 전파는 {@code samhan.realtime.broker=redis} 시점만 활성 (shared module
 * autoconfig).
 */
public class UserRealtimeBroker extends InMemoryRealtimeBroker {

    /** SSE event name — Employee/Department 변경 broadcast (audit overlay). */
    public static final String EVENT_EDIT = "user:edit";

    /** SSE event name — 수정/삭제 요청 생성. */
    public static final String EVENT_REQUEST_CREATED = "user:edit-request:created";

    /** SSE event name — 수정/삭제 요청 결정 (수락/거절/만료). */
    public static final String EVENT_REQUEST_DECIDED = "user:edit-request:decided";

    /**
     * Spring 이 RealtimePublishHook bean 등록 시 자동 setter 주입. Redis cross-node 활성 시점만.
     * slip-service 의 동일 패턴.
     */
    @Override
    @Autowired(required = false)
    public void setPublishHook(RealtimePublishHook hook) {
        super.setPublishHook(hook);
    }

    /**
     * UserRealtimeBroker bean 등록 — shared module 의 default RealtimeBroker bean 을 본 facade
     * 로 override.
     *
     * <p>{@code @ConditionalOnMissingBean(UserRealtimeBroker.class)} — 명시 override 보장.
     */
    @Configuration
    public static class UserRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(UserRealtimeBroker.class)
        public UserRealtimeBroker userRealtimeBrokerBean() {
            return new UserRealtimeBroker();
        }
    }
}
