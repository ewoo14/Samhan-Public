package com.samhanair.logis.notification.adapter.push;

import com.samhanair.logis.notification.adapter.NotificationGatewayResult;
import com.samhanair.logis.notification.domain.NotificationRequest;

/**
 * Test 용 PushAdapter — 모든 호출 즉시 success. Spring 컨텍스트 없이 단위 테스트 사용.
 *
 * <p>Spring bean 으로 등록하지 않음 (test profile 활성 시 별도 @Configuration 으로만 register).
 * IT 에서는 운영 {@link FcmPushAdapter} 를 그대로 사용하되 placeholder credentials 라 stub-success
 * 반환 (실 외부 호출 X) — 본 mock 는 단위 테스트 (NotificationGatewayTest / NotificationServiceTest) 전용.
 */
public class MockPushAdapter implements PushAdapter {

    @Override
    public NotificationGatewayResult send(NotificationRequest request) {
        return NotificationGatewayResult.success("mock-push-" + request.getId(), "{\"mock\":true}");
    }
}
