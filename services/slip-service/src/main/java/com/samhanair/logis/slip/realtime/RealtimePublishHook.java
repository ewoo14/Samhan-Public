package com.samhanair.logis.slip.realtime;

import java.util.UUID;

/**
 * cross-node propagate hook — PR-H2 (Phase 12 Step 2).
 *
 * <p>{@link SlipRealtimeBroker} 가 publish 직후 본 hook 을 호출하면 구현체가 다른 노드로 메시지
 * 전파 (Redis pub/sub, Kafka 등). 단일 노드 default 환경에서는 hook bean 미등록 (Optional.empty)
 * 로 SlipRealtimeBroker.publish 가 자기 노드만 처리.
 *
 * <p>구현체는 메시지 수신측 노드에서 {@link SlipRealtimeBroker#publishLocal} 호출 (infinite
 * loop 방지 — publish 가 아닌 publishLocal).
 */
public interface RealtimePublishHook {

    /**
     * 다른 노드로 SSE event 전파.
     *
     * @param slipId 대상 슬립
     * @param eventName SSE event name (예: "slip:edit", "comment.created")
     * @param payload event data (구현체가 직렬화 형식 결정 — Jackson JSON 권장)
     */
    void propagate(UUID slipId, String eventName, Object payload);
}
