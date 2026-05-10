package com.samhanair.logis.shared.realtime.broker;

import java.util.UUID;

/**
 * cross-node propagate hook — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>{@link RealtimeBroker} 가 publish 직후 본 hook 을 호출하면 구현체가 다른 노드로 메시지 전파
 * (Redis pub/sub, Kafka 등). 단일 노드 default 환경에서는 hook bean 미등록 (Optional.empty) 로
 * broker.publish 가 자기 노드만 처리.
 *
 * <p>구현체는 메시지 수신측 노드에서 {@link RealtimeBroker#publishLocal} 호출 의무 (infinite
 * loop 방지 — publish 가 아닌 publishLocal).
 *
 * <p>현 PR-H4a 표준 구현 = {@link RedisRealtimeBroker}. consumer service 가 추가 broker (예:
 * Kafka) 를 도입하려면 본 interface 만 구현하면 됨.
 */
public interface RealtimePublishHook {

    /**
     * 다른 노드로 SSE event 전파.
     *
     * @param entityId 대상 도메인 entity UUID
     * @param eventName SSE event name (예: "slip:edit", "comment.created")
     * @param payload event data (구현체가 직렬화 형식 결정 — Jackson JSON 권장)
     */
    void propagate(UUID entityId, String eventName, Object payload);
}
