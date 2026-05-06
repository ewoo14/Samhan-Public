package com.samhanair.logis.notification.adapter.email;

import com.samhanair.logis.notification.adapter.NotificationGateway;
import com.samhanair.logis.notification.domain.NotificationChannel;

/**
 * Email 채널 게이트웨이 marker — Phase 10 cutover 시점 AWS SES 활성화 대비.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link SesEmailAdapter} — Phase 10 활성 (현재 placeholder, 모든 호출 stub-success)</li>
 *   <li>{@link MockEmailAdapter} — test profile (모든 호출 success)</li>
 * </ul>
 */
public interface EmailAdapter extends NotificationGateway {

    @Override
    default NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
}
