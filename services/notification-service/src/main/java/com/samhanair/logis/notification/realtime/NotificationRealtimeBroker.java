package com.samhanair.logis.notification.realtime;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import com.samhanair.logis.shared.realtime.broker.RealtimePublishHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * notification-service SSE broker — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link InMemoryRealtimeBroker} thin facade. notification 도메인은 broker only —
 * push 채널 (delivered/failed 알림 + audit 변경 broadcast).
 *
 * <p><b>SSE event 표준</b>:
 * <ul>
 *   <li>{@code "notification:edit"} — PartnerChatRoomMapping/BlockedPartner 변경 broadcast</li>
 *   <li>{@code "notification:delivered"} — 발송 완료 push (read-only 표시)</li>
 *   <li>{@code "notification:failed"} — 발송 실패 push (재시도 안내)</li>
 * </ul>
 *
 * <p>Designer H4b-be-rollout-checklist § 3.3 = 알림 push 채널 명세.
 */
public class NotificationRealtimeBroker extends InMemoryRealtimeBroker {

    public static final String EVENT_EDIT = "notification:edit";
    public static final String EVENT_DELIVERED = "notification:delivered";
    public static final String EVENT_FAILED = "notification:failed";

    @Override
    @Autowired(required = false)
    public void setPublishHook(RealtimePublishHook hook) {
        super.setPublishHook(hook);
    }

    @Configuration
    public static class NotificationRealtimeBrokerConfig {

        @Bean
        @ConditionalOnMissingBean(NotificationRealtimeBroker.class)
        public NotificationRealtimeBroker notificationRealtimeBrokerBean() {
            return new NotificationRealtimeBroker();
        }
    }
}
