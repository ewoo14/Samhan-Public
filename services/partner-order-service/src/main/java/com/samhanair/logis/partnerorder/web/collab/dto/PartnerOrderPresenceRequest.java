package com.samhanair.logis.partnerorder.web.collab.dto;

/**
 * 주문 협업 presence join/leave 요청.
 *
 * <p>sessionId 는 클라이언트 mount 단위 opaque 식별자다.
 * displayName 은 선택값이며, X-User-Name 헤더가 있으면 헤더 값이 우선한다.
 */
public record PartnerOrderPresenceRequest(
        String sessionId,
        String displayName) {
}
