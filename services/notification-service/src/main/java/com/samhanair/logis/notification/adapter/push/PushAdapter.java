package com.samhanair.logis.notification.adapter.push;

import com.samhanair.logis.notification.adapter.NotificationGateway;
import com.samhanair.logis.notification.domain.NotificationChannel;

/**
 * Push 채널 게이트웨이 marker — FCM (Android Firebase Cloud Messaging) 기반.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link FcmPushAdapter} — 운영 (FCM credentials 필요)</li>
 *   <li>{@link MockPushAdapter} — test profile (모든 호출 success)</li>
 * </ul>
 */
public interface PushAdapter extends NotificationGateway {

    @Override
    default NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }
}
