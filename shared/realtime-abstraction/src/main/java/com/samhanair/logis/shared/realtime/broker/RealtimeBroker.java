package com.samhanair.logis.shared.realtime.broker;

import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 협업 SSE 브로커 — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 가 공통으로 사용하는 SSE pub/sub interface. 도메인별 entityId (UUID) 단위 구독 +
 * publish 모델. 도메인 = slip / inventory-lot / dispatch / partner-order / dashboard-kpi 등.
 *
 * <p><b>전송 = SseEmitter</b> (Spring 표준 servlet, spring-boot-starter-web 포함, 추가 의존 0).
 * 외부 SaaS (Pusher/Ably/PubNub) 의존 0 — Samhan Public 자체 운영 가능.
 *
 * <p><b>default 구현</b> = {@link InMemoryRealtimeBroker} (단일 노드). 다중 노드 환경에서는
 * {@link RedisRealtimeBroker} ({@code samhan.realtime.broker=redis}) 가 cross-node propagate 를 추가
 * 활성. consumer service 코드는 본 interface 만 의존하면 두 모드 모두 호환.
 *
 * <p><b>Thread-safety</b>: 구현체는 publish/subscribe/heartbeat 동시 호출 안전 의무.
 *
 * @since PR-H4a
 */
public interface RealtimeBroker {

    /**
     * 신규 SSE 구독 발급. emitter timeout 0L (무한) — heartbeat 가 keep-alive 책임.
     *
     * <p>완료/타임아웃/에러 콜백에서 자동 cleanup 의무.
     *
     * @param entityId 구독 대상 도메인 entity UUID (slip / lot / dispatch 등)
     * @return 신규 발급된 SseEmitter (controller 가 즉시 반환)
     */
    SseEmitter subscribe(UUID entityId);

    /**
     * 해당 entity 구독자 전원에게 SSE event 전송 (자기 노드) + cross-node hook 호출 (활성 시).
     *
     * @param entityId 대상 도메인 entity
     * @param eventName SSE event name (예: "comment.created", "slip:edit")
     * @param payload event data (Jackson 직렬화)
     */
    void publish(UUID entityId, String eventName, Object payload);

    /**
     * 자기 노드 emitter 들에게만 SSE event 전송 — cross-node hook 호출 없음.
     *
     * <p>{@link RedisRealtimeBroker} 가 다른 노드에서 메시지를 수신했을 때 본 메서드를 호출
     * (publish 호출 시 다시 Redis 로 전파되어 infinite loop 발생 — 방지 가드).
     *
     * @param entityId 대상 도메인 entity
     * @param eventName SSE event name
     * @param payload event data
     */
    void publishLocal(UUID entityId, String eventName, Object payload);

    /**
     * 30초 주기 heartbeat — 끊긴 emitter 감지 + proxy idle timeout 회피.
     * 구현체가 {@code @Scheduled} 로 호출 (단위 테스트는 직접 호출 가능).
     */
    void heartbeat();

    /**
     * 특정 entity 구독자 수 (운영 모니터링 / 테스트 검증).
     *
     * @param entityId 대상 entity
     * @return 현재 활성 구독자 수
     */
    int subscriberCount(UUID entityId);

    /** 누적 publish 시도 횟수 (테스트/운영 검증). */
    long publishCount();

    /** 누적 publish 실패 횟수 (IOException cleanup). */
    long publishFailureCount();

    /** 누적 heartbeat 실행 횟수. */
    long heartbeatCount();
}
