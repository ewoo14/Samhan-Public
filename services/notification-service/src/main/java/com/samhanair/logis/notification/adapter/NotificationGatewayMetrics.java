package com.samhanair.logis.notification.adapter;

import com.samhanair.logis.notification.domain.NotificationChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 채널별 발송 결과 Micrometer counter — post-W5 backlog cleanup (DevOps 채택, D-P9-21).
 *
 * <p>metric: {@code notification_gateway_send_total{channel,result}}.
 * actuator/prometheus endpoint 에서 노출 → CloudWatch / Prometheus 시계열 적재 → Grafana / CloudWatch
 * dashboard 에서 channel × result 매트릭스 시각화 (Phase 10 monitoring 활성).
 *
 * <ul>
 *   <li>{@code channel} tag — PUSH / EMAIL / SMS</li>
 *   <li>{@code result} tag — success / failure</li>
 * </ul>
 *
 * <p>{@link NotificationGateway} 인터페이스 자체는 변경 0 — 본 컴포넌트가 별도 wrapper 로
 * service 레이어에서 호출. 회귀 안전성 우선 (기존 adapter / IT 영향 0).
 *
 * <p>주입 시점에 3 channel × 2 result = 6 counter 사전 등록 (lazy 등록 race 방지).
 */
@Component
public class NotificationGatewayMetrics {

    /** counter 이름 — Prometheus naming convention (lowercase + underscore + _total suffix). */
    public static final String COUNTER_NAME = "notification_gateway_send_total";

    private final Map<NotificationChannel, Counter> successCounters;
    private final Map<NotificationChannel, Counter> failureCounters;

    public NotificationGatewayMetrics(MeterRegistry meterRegistry) {
        this.successCounters = new EnumMap<>(NotificationChannel.class);
        this.failureCounters = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            this.successCounters.put(channel, Counter.builder(COUNTER_NAME)
                    .description("notification gateway 발송 결과 누적 카운터")
                    .tag("channel", channel.name())
                    .tag("result", "success")
                    .register(meterRegistry));
            this.failureCounters.put(channel, Counter.builder(COUNTER_NAME)
                    .description("notification gateway 발송 결과 누적 카운터")
                    .tag("channel", channel.name())
                    .tag("result", "failure")
                    .register(meterRegistry));
        }
    }

    /** 성공 increment — service 레이어가 gateway success 결과 시점에 호출. */
    public void recordSuccess(NotificationChannel channel) {
        Counter c = successCounters.get(channel);
        if (c != null) {
            c.increment();
        }
    }

    /** 실패 increment — service 레이어가 gateway failure 결과 또는 예외 시점에 호출. */
    public void recordFailure(NotificationChannel channel) {
        Counter c = failureCounters.get(channel);
        if (c != null) {
            c.increment();
        }
    }
}
