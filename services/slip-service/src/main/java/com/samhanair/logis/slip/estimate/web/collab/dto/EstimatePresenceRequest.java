package com.samhanair.logis.slip.estimate.web.collab.dto;

/**
 * 견적 협업 presence join/leave 요청.
 *
 * <p>{@code sessionId} 는 클라이언트 mount 단위 opaque 식별자다.
 * account UUID 와는 별개이며, 사용자에게 노출되지 않는다.
 */
public record EstimatePresenceRequest(
        String sessionId,
        String displayName) {
}
