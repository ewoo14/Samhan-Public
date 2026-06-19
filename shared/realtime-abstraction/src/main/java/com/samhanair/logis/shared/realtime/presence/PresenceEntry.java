package com.samhanair.logis.shared.realtime.presence;

import java.time.Instant;

/**
 * 특정 entity 를 현재 보고 있는 사용자 1명의 presence snapshot.
 *
 * @param userId 내부 식별자. API/client state 전용이며 화면 표시 금지.
 * @param displayName 화면에 표시할 사용자명.
 * @param color userId hash 로 결정된 avatar 색상.
 * @param lastSeenAt TTL 정제 기준 시각.
 */
public record PresenceEntry(
        String userId,
        String displayName,
        PresenceColor color,
        Instant lastSeenAt) {

    public PresenceEntry withLastSeenAt(Instant nextLastSeenAt) {
        return new PresenceEntry(userId, displayName, color, nextLastSeenAt);
    }
}
