package com.samhanair.logis.groupware.web.collab.dto;

/**
 * 결재 협업 presence join/leave 요청.
 *
 * <p>{@code sessionId} 는 클라이언트 mount 단위 opaque 식별자이며 account UUID 가 아니다.
 * {@code displayName} 은 선택 필드로 헤더 {@code X-User-Name} 이 우선 적용된다.
 */
public record ApprovalPresenceRequest(
        String sessionId,
        String displayName) {
}
