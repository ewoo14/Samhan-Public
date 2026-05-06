package com.samhanair.logis.notification.adapter;

import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.NotificationRequest;

/**
 * 채널별 게이트웨이 공통 인터페이스. {@code FcmPushAdapter} / {@code SesEmailAdapter} /
 * {@code AligoSmsAdapter} 가 본 인터페이스를 구현하고 채널 enum 으로 strategy lookup.
 *
 * <p>구현체는 send 호출 시 외부 게이트웨이를 1회 호출하고 결과를 {@link NotificationGatewayResult}
 * 로 반환. 예외 throw 가 아닌 result 반환으로 일관 — service 레이어가 attempt 누적 결정 (재시도 정책 분리).
 */
public interface NotificationGateway {

    /** 본 게이트웨이가 담당하는 채널. */
    NotificationChannel channel();

    /**
     * 게이트웨이 호출 — 외부 API 1회 호출.
     *
     * @param request 발송 요청 (recipient / body / template / payload)
     * @return 게이트웨이 응답 — success / failure / messageId / raw response
     */
    NotificationGatewayResult send(NotificationRequest request);
}
