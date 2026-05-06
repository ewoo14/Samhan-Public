package com.samhanair.logis.notification.dto;

import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.NotificationRequest;
import com.samhanair.logis.notification.domain.NotificationStatus;
import com.samhanair.logis.notification.domain.RecipientType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 발송 상태 응답 DTO — Internal endpoint {@code GET /internal/notifications/{id}/status} +
 * Admin 단건 조회 공용.
 *
 * <p>UUID 비공개 가드 — 본 응답은 형제 service 또는 admin 한정 (사용자 화면 직접 노출 X).
 *
 * @param requestId 발송 요청 식별자
 * @param recipientType 수신자 타입
 * @param recipientId 수신자 UUID (USER / PARTNER 만)
 * @param channel 발송 채널
 * @param status 라이프사이클 상태
 * @param attemptCount 시도 횟수
 * @param lastAttemptedAt 마지막 시도 시각 (null 가능)
 */
public record NotificationStatusResponse(
        UUID requestId,
        RecipientType recipientType,
        UUID recipientId,
        NotificationChannel channel,
        NotificationStatus status,
        int attemptCount,
        LocalDateTime lastAttemptedAt
) {

    public static NotificationStatusResponse from(NotificationRequest req) {
        return new NotificationStatusResponse(
                req.getId(),
                req.getRecipientType(),
                req.getRecipientId(),
                req.getChannel(),
                req.getStatus(),
                req.getAttemptCount(),
                req.getLastAttemptedAt());
    }
}
