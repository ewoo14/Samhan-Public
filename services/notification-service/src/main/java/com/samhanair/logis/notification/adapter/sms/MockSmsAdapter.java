package com.samhanair.logis.notification.adapter.sms;

import com.samhanair.logis.notification.adapter.NotificationGatewayResult;
import com.samhanair.logis.notification.domain.NotificationRequest;

/**
 * Test 용 SmsAdapter — 모든 호출 즉시 success. 단위 테스트 전용.
 */
public class MockSmsAdapter implements SmsAdapter {

    @Override
    public NotificationGatewayResult send(NotificationRequest request) {
        return NotificationGatewayResult.success("mock-sms-" + request.getId(), "{\"mock\":true}");
    }
}
