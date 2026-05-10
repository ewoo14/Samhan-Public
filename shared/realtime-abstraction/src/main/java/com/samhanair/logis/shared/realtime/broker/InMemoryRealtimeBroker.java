package com.samhanair.logis.shared.realtime.broker;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * in-memory SSE 브로커 — PR-H4a (Phase 12 Step 4a) {@link RealtimeBroker} default 구현.
 *
 * <p><b>전송 = SseEmitter</b> (Spring 표준 servlet, spring-boot-starter-web 포함, 추가 의존 0).
 * 외부 SaaS (Pusher/Ably/PubNub) 의존 0 — Samhan Public 자체 운영 가능.
 *
 * <p><b>다중 노드 가정</b>: 단일 노드 in-memory broker. 다중 노드 환경에서는 {@link RealtimePublishHook}
 * 구현체 ({@link RedisRealtimeBroker} 등) 가 hook 으로 등록되어 cross-node 전파.
 *
 * <p><b>Emitter 라이프사이클</b>:
 * <ul>
 *   <li>{@link #subscribe(UUID)} — emitter 신규 발급, timeout 0L (무한 — heartbeat 로 keep-alive).
 *       완료/타임아웃/에러 콜백에서 자동 cleanup.</li>
 *   <li>{@link #publish(UUID, String, Object)} — 해당 entityId 구독자 전원에게 SSE event 전송.
 *       IOException 발생 emitter 는 즉시 제거 (cleanup).</li>
 *   <li>{@link #heartbeat()} — 30초마다 모든 구독자에게 {@code ping} comment 전송. 끊긴 emitter
 *       감지 + proxy/load-balancer idle timeout 회피.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b>: emitters {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} 조합 —
 * publish/subscribe/heartbeat 동시 호출 안전. SseEmitter.send 는 내부 동기화.
 */
@Slf4j
public class InMemoryRealtimeBroker implements RealtimeBroker {

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
     * cross-node propagate hook — {@link RealtimePublishHook} 구현체가 활성화되면 본 hook 으로
     * 등록되어 publish 시 cross-node 호출. 단일 노드 default 환경에서는 hook 없음 (no-op).
     *
     * <p>본 broker 는 항상 자기 노드의 emitter 들에게 publish 한 뒤 hook 도 호출 (cross-node
     * 전파). hook 자체는 수신측 노드의 본 broker.publishLocal 을 다시 호출하도록 호출자가 책임
     * (Redis 메시지 수신 시 publishLocal — infinite loop 방지).
     */
    private Optional<RealtimePublishHook> publishHook = Optional.empty();

    /**
     * Spring 이 RealtimePublishHook bean 등록 시 자동 setter 주입. hook bean 미등록 (default
     * 단일 노드) 환경에서는 hook 미설정 (Optional.empty).
     *
     * <p>{@code @Autowired(required=false)} 로 단일 노드 default 환경 startup 정상 보장.
     */
    @Autowired(required = false)
    public void setPublishHook(RealtimePublishHook hook) {
        this.publishHook = Optional.ofNullable(hook);
        if (hook != null) {
            log.info("[PR-H4a] cross-node propagate hook 등록됨 — broker={}",
                    hook.getClass().getSimpleName());
        }
    }

    @Override
    public SseEmitter subscribe(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId 는 필수입니다");
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_INFINITE);

        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(entityId,
                k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(entityId, emitter));
        emitter.onTimeout(() -> remove(entityId, emitter));
        emitter.onError(throwable -> remove(entityId, emitter));

        // 초기 connect event — 클라이언트 onopen 직후 1회 신호
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("entityId", entityId.toString())));
        } catch (IOException ex) {
            log.debug("SSE 초기 connected event 전송 실패 — emitter 즉시 제거 entityId={}", entityId);
            remove(entityId, emitter);
        }
        return emitter;
    }

    @Override
    public void publish(UUID entityId, String eventName, Object payload) {
        publishLocal(entityId, eventName, payload);
        // cross-node propagate (활성 시) — hook 자체는 수신측에서 publishLocal 만 호출 (loop 방지)
        publishHook.ifPresent(hook -> {
            try {
                hook.propagate(entityId, eventName, payload);
            } catch (RuntimeException ex) {
                log.warn("[PR-H4a] cross-node propagate 실패 — entityId={} event={} cause={}",
                        entityId, eventName, ex.getMessage());
            }
        });
    }

    @Override
    public void publishLocal(UUID entityId, String eventName, Object payload) {
        Objects.requireNonNull(entityId, "entityId 는 필수입니다");
        Objects.requireNonNull(eventName, "eventName 은 필수입니다");
        publishCount.incrementAndGet();

        CopyOnWriteArrayList<SseEmitter> list = emitters.get(entityId);
        if (list == null || list.isEmpty()) {
            return;
        }

        Set<SseEmitter> dead = new HashSet<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                publishFailureCount.incrementAndGet();
                log.debug("SSE publish 실패 — emitter cleanup entityId={} event={} cause={}",
                        entityId, eventName, ex.getMessage());
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            list.removeAll(dead);
            if (list.isEmpty()) {
                emitters.remove(entityId, list);
            }
        }
    }

    @Override
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        heartbeatCount.incrementAndGet();
        for (Map.Entry<UUID, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            UUID entityId = entry.getKey();
            CopyOnWriteArrayList<SseEmitter> list = entry.getValue();
            Set<SseEmitter> dead = new HashSet<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    log.debug("SSE heartbeat 실패 — emitter cleanup entityId={}", entityId);
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                list.removeAll(dead);
                if (list.isEmpty()) {
                    emitters.remove(entityId, list);
                }
            }
        }
    }

    @Override
    public int subscriberCount(UUID entityId) {
        List<SseEmitter> list = emitters.get(entityId);
        return list == null ? 0 : list.size();
    }

    @Override
    public long publishCount() {
        return publishCount.get();
    }

    @Override
    public long publishFailureCount() {
        return publishFailureCount.get();
    }

    @Override
    public long heartbeatCount() {
        return heartbeatCount.get();
    }

    private void remove(UUID entityId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(entityId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(entityId, list);
            }
        }
    }
}
