package com.samhanair.logis.notification.adapter.sms;

import com.samhanair.logis.notification.adapter.NotificationGateway;
import com.samhanair.logis.notification.domain.NotificationChannel;

/**
 * SMS 채널 게이트웨이 marker — 한국 SMS 게이트웨이 (Aligo).
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link AligoSmsAdapter} — 운영 (Aligo apis.aligo.in/send/ form-urlencoded)</li>
 *   <li>{@link MockSmsAdapter} — test profile (모든 호출 success)</li>
 * </ul>
 *
 * <p>Phase 5 의 {@code services/slip-service/.../sms/AligoSmsGateway.java} 흡수 — 동일 form-urlencoded
 * 호출 모델, key/userid/sender/receiver/msg 파라미터 일관.
 */
public interface SmsAdapter extends NotificationGateway {

    @Override
    default NotificationChannel channel() {
        return NotificationChannel.SMS;
    }
}
