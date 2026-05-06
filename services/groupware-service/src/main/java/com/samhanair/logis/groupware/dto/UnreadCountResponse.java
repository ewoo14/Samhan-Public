package com.samhanair.logis.groupware.dto;

import java.util.UUID;

/**
 * 미열람 메신저 카운트 응답 DTO — Internal endpoint.
 *
 * @param userId 대상 user UUID
 * @param unreadCount 미열람 메신저 수
 */
public record UnreadCountResponse(
        UUID userId,
        long unreadCount
) {
}
