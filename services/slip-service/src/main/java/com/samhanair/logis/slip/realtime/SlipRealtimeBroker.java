package com.samhanair.logis.slip.realtime;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 슬립 실시간 SSE 브로커 — PR-H1 (Phase 12 Step 1).
 *
 * <p><b>전송 = SseEmitter</b> (Spring 표준 servlet, spring-boot-starter-web 포함, 추가 의존 0).
 * 외부 SaaS (Pusher/Ably/PubNub) 의존 0 — Samhan Public 자체 운영 가능.
 *
 * <p><b>다중 노드 가정</b>: 단일 노드 in-memory broker. Phase 12 Step 1 = 단일 slip-service 인스턴스.
 * 향후 다중 노드 확장 시 Redis pub/sub 또는 Kafka 로 broker 교체 (interface 추출).
 *
 * <p><b>Emitter 라이프사이클</b>:
 * <ul>
 *   <li>{@link #subscribe(UUID)} — emitter 신규 발급, timeout 0L (무한 — heartbeat 로 keep-alive).
 *       완료/타임아웃/에러 콜백에서 자동 cleanup.</li>
 *   <li>{@link #publish(UUID, String, Object)} — 해당 slipId 구독자 전원에게 SSE event 전송.
 *       IOException 발생 emitter 는 즉시 제거 (cleanup).</li>
 *   <li>{@link #heartbeat()} — 30초마다 모든 구독자에게 {@code ping} comment 전송. 끊긴 emitter
 *       감지 + proxy/load-balancer idle timeout 회피.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b>: emitters {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} 조합 —
 * publish/subscribe/heartbeat 동시 호출 안전. SseEmitter.send 는 내부 동기화.
 */
@Slf4j
@Component
public class SlipRealtimeBroker {

    /** Emitter timeout 0L = 무한 (heartbeat keep-alive). */
    public static final long EMITTER_TIMEOUT_INFINITE = 0L;

    /** Heartbeat interval ms (30s) — proxy idle timeout (보통 60s) 보다 짧게. */
    public static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 통계 — 누적 publish 시도/실패/heartbeat 카운터 (운영 모니터링용). */
    private final AtomicLong publishCount = new AtomicLong();
    private final AtomicLong publishFailureCount = new AtomicLong();
    private final AtomicLong heartbeatCount = new AtomicLong();

    /**
     * 신규 SSE 구독 발급. emitter timeout 0L 무한 — heartbeat 가 keep-alive.
     *
     * <p>완료/타임아웃/에러 콜백에서 자동 cleanup.
     *
     * @param slipId 구독할 슬립 UUID
     * @return 신규 발급된 SseEmitter (controller 가 즉시 반환)
     */
    public SseEmitter subscribe(UUID slipId) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_INFINITE);

        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(slipId,
                k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(slipId, emitter));
        emitter.onTimeout(() -> remove(slipId, emitter));
        emitter.onError(throwable -> remove(slipId, emitter));

        // 초기 connect event — 클라이언트 onopen 직후 1회 신호
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("slipId", slipId.toString())));
        } catch (IOException ex) {
            log.debug("SSE 초기 connected event 전송 실패 — emitter 즉시 제거 slipId={}", slipId);
            remove(slipId, emitter);
        }
        return emitter;
    }

    /**
     * 해당 슬립 구독자 전원에게 SSE event 전송. IOException 발생 emitter 는 즉시 cleanup.
     *
     * @param slipId 대상 슬립
     * @param eventName SSE event name (예: "comment.created")
     * @param payload event data (Jackson 직렬화)
     */
    public void publish(UUID slipId, String eventName, Object payload) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        Objects.requireNonNull(eventName, "eventName 은 필수입니다");
        publishCount.incrementAndGet();

        CopyOnWriteArrayList<SseEmitter> list = emitters.get(slipId);
        if (list == null || list.isEmpty()) {
            return;
        }

        Set<SseEmitter> dead = new HashSet<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                publishFailureCount.incrementAndGet();
                log.debug("SSE publish 실패 — emitter cleanup slipId={} event={} cause={}",
                        slipId, eventName, ex.getMessage());
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            list.removeAll(dead);
            if (list.isEmpty()) {
                emitters.remove(slipId, list);
            }
        }
    }

    /**
     * 30초 주기 heartbeat — 끊긴 emitter 감지 + proxy idle timeout 회피.
     *
     * <p>SSE comment ({@code :ping}) 형식 — event 가 아닌 comment line 으로 트래픽 최소화.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        heartbeatCount.incrementAndGet();
        for (Map.Entry<UUID, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            UUID slipId = entry.getKey();
            CopyOnWriteArrayList<SseEmitter> list = entry.getValue();
            Set<SseEmitter> dead = new HashSet<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    log.debug("SSE heartbeat 실패 — emitter cleanup slipId={}", slipId);
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                list.removeAll(dead);
                if (list.isEmpty()) {
                    emitters.remove(slipId, list);
                }
            }
        }
    }

    /**
     * 특정 슬립 구독자 수 (운영 모니터링 / 테스트 검증).
     *
     * @param slipId 대상 슬립
     * @return 현재 활성 구독자 수
     */
    public int subscriberCount(UUID slipId) {
        List<SseEmitter> list = emitters.get(slipId);
        return list == null ? 0 : list.size();
    }

    /** 누적 publish 시도 횟수 (테스트/운영 검증). */
    public long publishCount() {
        return publishCount.get();
    }

    /** 누적 publish 실패 횟수 (IOException cleanup). */
    public long publishFailureCount() {
        return publishFailureCount.get();
    }

    /** 누적 heartbeat 실행 횟수. */
    public long heartbeatCount() {
        return heartbeatCount.get();
    }

    private void remove(UUID slipId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(slipId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(slipId, list);
            }
        }
    }
}
