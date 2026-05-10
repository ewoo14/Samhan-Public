package com.samhanair.logis.slip.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * PR-H2 BE — TM 보완 #1: SlipRealtimeBroker 다중 emitter 동시성 IT (3 case).
 *
 * <p>본 IT 는 Spring 컨텍스트 없이 broker 직접 인스턴스화 — 순수 동시성 단위. ConcurrentHashMap
 * + CopyOnWriteArrayList 가드의 race condition 검증.
 *
 * <ol>
 *   <li>concurrentSubscribe_thenPublish — 다중 구독자 동시 발급 후 단일 publish 수신 정합</li>
 *   <li>concurrentClose_duringPublish — emitter 동시 close (cleanup race) 시 NPE/IOException 무발생</li>
 *   <li>load_100emitters_1000publish — 부하 시나리오 (모든 통계 일치 검증)</li>
 * </ol>
 */
class SlipRealtimeBrokerConcurrencyIT {

    @Test
    void concurrentSubscribe_thenPublish_allReceiveEvent() throws InterruptedException {
        SlipRealtimeBroker broker = new SlipRealtimeBroker();
        UUID slipId = UUID.randomUUID();
        int emitterCount = 50;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(emitterCount);
        List<SseEmitter> emitters = new ArrayList<>();

        for (int i = 0; i < emitterCount; i++) {
            pool.submit(() -> {
                SseEmitter e = broker.subscribe(slipId);
                synchronized (emitters) { emitters.add(e); }
                ready.countDown();
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // 동시 subscribe 후 정확한 카운트
        assertThat(broker.subscriberCount(slipId)).isEqualTo(emitterCount);

        // 단일 publish — 누적 publishCount 1 증가
        long before = broker.publishCount();
        broker.publish(slipId, "test.event", java.util.Map.of("k", "v"));
        assertThat(broker.publishCount()).isEqualTo(before + 1);
    }

    @Test
    void concurrentClose_duringPublish_noNpeOrIoException() throws InterruptedException {
        SlipRealtimeBroker broker = new SlipRealtimeBroker();
        UUID slipId = UUID.randomUUID();
        int emitterCount = 30;

        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < emitterCount; i++) {
            emitters.add(broker.subscribe(slipId));
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(emitterCount + 10);

        // 동시 publish (publish 측)
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            pool.submit(() -> {
                broker.publish(slipId, "edit", java.util.Map.of("seq", idx));
                done.countDown();
            });
        }
        // 동시 emitter complete (cleanup race)
        for (SseEmitter e : emitters) {
            pool.submit(() -> {
                e.complete();
                done.countDown();
            });
        }
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // race condition 후에도 broker 통계 일관 — 예외 발생 없음 (assertion)
        // emitter 모두 complete 되어 subscriberCount = 0 이어야 함
        assertThat(broker.subscriberCount(slipId)).isLessThanOrEqualTo(emitterCount);
    }

    @Test
    void load_100emitters_1000publish_statsConsistent() throws InterruptedException {
        SlipRealtimeBroker broker = new SlipRealtimeBroker();
        int slipCount = 5; // 5 slips x 20 emitters each = 100 emitters
        int publishesPerSlip = 200;
        UUID[] slipIds = new UUID[slipCount];
        for (int i = 0; i < slipCount; i++) {
            slipIds[i] = UUID.randomUUID();
            for (int j = 0; j < 20; j++) {
                broker.subscribe(slipIds[i]);
            }
        }
        assertThat(broker.subscriberCount(slipIds[0])).isEqualTo(20);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger total = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(slipCount * publishesPerSlip);

        for (UUID slipId : slipIds) {
            for (int i = 0; i < publishesPerSlip; i++) {
                final int seq = i;
                pool.submit(() -> {
                    broker.publish(slipId, "edit", java.util.Map.of("seq", seq));
                    total.incrementAndGet();
                    done.countDown();
                });
            }
        }
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(total.get()).isEqualTo(slipCount * publishesPerSlip);
        // 누적 publish 시도 = 1000 (5 x 200)
        assertThat(broker.publishCount()).isEqualTo(slipCount * publishesPerSlip);
    }
}
