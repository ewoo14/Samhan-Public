package com.samhanair.logis.notification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.notification.domain.NotificationChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * NotificationGatewayMetrics 단위 테스트 — post-W5 backlog cleanup (DevOps, D-P9-21).
 *
 * <p>SimpleMeterRegistry 로 in-memory 검증. Spring 부팅 없음 — JDK 17 한글 path 환경에서도 PASS.
 *
 * <ul>
 *   <li>3 channel × 2 result = 6 counter 사전 등록 검증</li>
 *   <li>recordSuccess / recordFailure → counter increment 검증</li>
 * </ul>
 */
class NotificationGatewayMetricsTest {

    @Test
    void send_recordsSuccessCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        NotificationGatewayMetrics metrics = new NotificationGatewayMetrics(registry);

        // 사전 등록된 6 counter 검증 (3 channel × 2 result)
        assertThat(registry.find(NotificationGatewayMetrics.COUNTER_NAME)
                .tag("channel", "PUSH").tag("result", "success").counter()).isNotNull();
        assertThat(registry.find(NotificationGatewayMetrics.COUNTER_NAME)
                .tag("channel", "EMAIL").tag("result", "failure").counter()).isNotNull();

        // PUSH success 1회 increment
        metrics.recordSuccess(NotificationChannel.PUSH);

        double pushSuccess = registry.find(NotificationGatewayMetrics.COUNTER_NAME)
                .tag("channel", "PUSH").tag("result", "success").counter().count();
        assertThat(pushSuccess).isEqualTo(1.0);
    }

    @Test
    void send_recordsFailureCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        NotificationGatewayMetrics metrics = new NotificationGatewayMetrics(registry);

        // SMS failure 3회 increment
        metrics.recordFailure(NotificationChannel.SMS);
        metrics.recordFailure(NotificationChannel.SMS);
        metrics.recordFailure(NotificationChannel.SMS);

        double smsFailure = registry.find(NotificationGatewayMetrics.COUNTER_NAME)
                .tag("channel", "SMS").tag("result", "failure").counter().count();
        assertThat(smsFailure).isEqualTo(3.0);

        // EMAIL failure 0건 (다른 채널 격리 검증)
        double emailFailure = registry.find(NotificationGatewayMetrics.COUNTER_NAME)
                .tag("channel", "EMAIL").tag("result", "failure").counter().count();
        assertThat(emailFailure).isEqualTo(0.0);
    }
}
