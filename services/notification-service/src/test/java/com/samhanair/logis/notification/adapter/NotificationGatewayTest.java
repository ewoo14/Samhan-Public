package com.samhanair.logis.notification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.notification.adapter.email.MockEmailAdapter;
import com.samhanair.logis.notification.adapter.push.MockPushAdapter;
import com.samhanair.logis.notification.adapter.sms.MockSmsAdapter;
import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.NotificationRequest;
import com.samhanair.logis.notification.domain.RecipientType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 채널별 게이트웨이 strategy 단위 검증 — 3 case (PUSH / EMAIL / SMS).
 *
 * <p>각 mock 어댑터가 자신이 담당하는 채널 enum 을 노출하고 send 호출 시 success 결과를 반환하는지 검증.
 * Spring 컨텍스트 부팅 없음 — 한글 path 환경에서도 PASS.
 */
class NotificationGatewayTest {

    @Test
    void mock_push_adapter_returns_success() {
        NotificationGateway gateway = new MockPushAdapter();
        NotificationRequest req = NotificationRequest.open(
                RecipientType.USER, UUID.randomUUID(), null,
                NotificationChannel.PUSH, null, "제목", "본문", null);

        NotificationGatewayResult result = gateway.send(req);

        assertThat(gateway.channel()).isEqualTo(NotificationChannel.PUSH);
        assertThat(result.success()).isTrue();
        assertThat(result.gatewayStatus()).isEqualTo("SUCCESS");
        assertThat(result.messageId()).startsWith("mock-push-");
    }

    @Test
    void mock_email_adapter_returns_success() {
        NotificationGateway gateway = new MockEmailAdapter();
        NotificationRequest req = NotificationRequest.open(
                RecipientType.USER, UUID.randomUUID(), "user@samhan.test",
                NotificationChannel.EMAIL, null, "제목", "본문", null);

        NotificationGatewayResult result = gateway.send(req);

        assertThat(gateway.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).startsWith("mock-email-");
    }

    @Test
    void mock_sms_adapter_returns_success() {
        NotificationGateway gateway = new MockSmsAdapter();
        NotificationRequest req = NotificationRequest.open(
                RecipientType.EXTERNAL_PHONE, null, "01012345678",
                NotificationChannel.SMS, null, null, "본문", null);

        NotificationGatewayResult result = gateway.send(req);

        assertThat(gateway.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).startsWith("mock-sms-");
    }
}
