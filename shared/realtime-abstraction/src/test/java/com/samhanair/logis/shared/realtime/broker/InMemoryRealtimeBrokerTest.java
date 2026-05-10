package com.samhanair.logis.shared.realtime.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * PR-H4a — InMemoryRealtimeBroker 단위 테스트 (4 case).
 *
 * <ol>
 *   <li>subscribe — emitter 발급, subscriberCount 증가, 초기 connected event 전송</li>
 *   <li>publish — 정상 emitter 는 cleanup 안됨, publishCount 증가</li>
 *   <li>publish — 끊긴 emitter (complete 된) → IllegalStateException → cleanup</li>
 *   <li>heartbeat — heartbeatCount 증가 + 끊긴 emitter cleanup</li>
 * </ol>
 */
class InMemoryRealtimeBrokerTest {

    private InMemoryRealtimeBroker broker;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        broker = new InMemoryRealtimeBroker();
        entityId = UUID.randomUUID();
    }

    @Test
    void subscribe_increasesSubscriberCount() {
        SseEmitter emitter = broker.subscribe(entityId);

        assertThat(emitter).isNotNull();
        assertThat(broker.subscriberCount(entityId)).isEqualTo(1);
    }

    @Test
    void publish_normalEmitters_notCleanedUp() {
        broker.subscribe(entityId);
        broker.subscribe(entityId);

        long before = broker.publishCount();
        broker.publish(entityId, "comment.created", Map.of("k", "v"));

        assertThat(broker.publishCount()).isEqualTo(before + 1);
        assertThat(broker.subscriberCount(entityId)).isEqualTo(2);
    }

    @Test
    void publish_completedEmitter_isCleanedUp() {
        UUID isolated = UUID.randomUUID();
        SseEmitter completed = broker.subscribe(isolated);
        completed.complete();

        broker.publish(isolated, "comment.created", Map.of("k", "v"));

        assertThat(broker.subscriberCount(isolated)).isZero();
        assertThat(broker.publishFailureCount()).isGreaterThan(0L);
    }

    @Test
    void heartbeat_incrementsCountAndCleansClosedEmitters() {
        SseEmitter emitter = broker.subscribe(entityId);
        emitter.complete();

        long before = broker.heartbeatCount();
        broker.heartbeat();

        assertThat(broker.heartbeatCount()).isEqualTo(before + 1);
        assertThat(broker.subscriberCount(entityId)).isZero();
    }
}
