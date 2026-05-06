package com.samhanair.logis.notification.adapter.email;

import com.samhanair.logis.notification.adapter.NotificationGatewayResult;
import com.samhanair.logis.notification.domain.NotificationRequest;

/**
 * Test 용 EmailAdapter — 모든 호출 즉시 success. 단위 테스트 전용.
 */
public class MockEmailAdapter implements EmailAdapter {

    @Override
    public NotificationGatewayResult send(NotificationRequest request) {
        return NotificationGatewayResult.success("mock-email-" + request.getId(), "{\"mock\":true}");
    }
}
