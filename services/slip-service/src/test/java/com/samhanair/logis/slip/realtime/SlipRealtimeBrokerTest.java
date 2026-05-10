package com.samhanair.logis.slip.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * PR-H1 BE — SlipRealtimeBroker 단위 테스트.
 *
 * <p>Test case:
 * <ol>
 *   <li>subscribe — emitter 발급, subscriberCount 증가, 초기 connected event 전송 (정상)</li>
 *   <li>publish — 정상 emitter 는 cleanup 안됨, publishCount 증가</li>
 *   <li>publish — 끊긴 emitter (complete 된) → IllegalStateException → cleanup</li>
 *   <li>heartbeat — heartbeatCount 증가 + 끊긴 emitter cleanup</li>
 * </ol>
 */
class SlipRealtimeBrokerTest {

    private SlipRealtimeBroker broker;
    private UUID slipId;

    @BeforeEach
    void setUp() {
        broker = new SlipRealtimeBroker();
        slipId = UUID.randomUUID();
    }

    @Test
    void subscribe_increasesSubscriberCount() {
        SseEmitter emitter = broker.subscribe(slipId);

        assertThat(emitter).isNotNull();
        assertThat(broker.subscriberCount(slipId)).isEqualTo(1);
    }

    @Test
    void publish_normalEmitters_notCleanedUp() {
        broker.subscribe(slipId);
        broker.subscribe(slipId);

        long before = broker.publishCount();
        broker.publish(slipId, "comment.created", Map.of("k", "v"));

        assertThat(broker.publishCount()).isEqualTo(before + 1);
        // 정상 emitter (테스트 환경에서도 send 자체는 buffer 에만 작성) 는 cleanup 미발생
        assertThat(broker.subscriberCount(slipId)).isEqualTo(2);
    }

    @Test
    void publish_completedEmitter_isCleanedUp() {
        UUID isolated = UUID.randomUUID();
        SseEmitter completed = broker.subscribe(isolated);
        completed.complete();

        // publish 시 completed emitter send → IllegalStateException → cleanup
        broker.publish(isolated, "comment.created", Map.of("k", "v"));

        assertThat(broker.subscriberCount(isolated)).isZero();
        assertThat(broker.publishFailureCount()).isGreaterThan(0L);
    }

    @Test
    void heartbeat_incrementsCountAndCleansClosedEmitters() {
        SseEmitter emitter = broker.subscribe(slipId);
        emitter.complete();

        long before = broker.heartbeatCount();
        broker.heartbeat();

        assertThat(broker.heartbeatCount()).isEqualTo(before + 1);
        // complete() 한 emitter 는 heartbeat send 시 IllegalStateException → cleanup
        assertThat(broker.subscriberCount(slipId)).isZero();
    }
}
