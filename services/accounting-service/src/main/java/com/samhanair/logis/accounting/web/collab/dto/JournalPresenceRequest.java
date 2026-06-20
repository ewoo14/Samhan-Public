package com.samhanair.logis.accounting.web.collab.dto;

/**
 * 회계전표 협업 presence join/leave 요청.
 *
 * <p>{@code sessionId} 는 클라이언트 mount 단위 opaque 식별자이며, account UUID 가 아니다.
 * {@code displayName} 은 body 로 전달되지만, {@code X-User-Name} 헤더가 존재할 경우 헤더 값이 우선한다.
 */
public record JournalPresenceRequest(
        String sessionId,
        String displayName) {
}
