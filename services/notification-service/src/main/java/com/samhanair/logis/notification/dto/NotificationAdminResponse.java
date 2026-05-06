package com.samhanair.logis.notification.dto;

import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.NotificationRequest;
import com.samhanair.logis.notification.domain.NotificationStatus;
import com.samhanair.logis.notification.domain.RecipientType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자용 발송 요청 응답 DTO — admin 화면이 본문 / 템플릿 등 전체 노출.
 *
 * @param requestId 발송 요청 식별자
 * @param recipientType 수신자 타입
 * @param recipientId 수신자 UUID
 * @param recipientAddress 보조 주소 / 전화번호
 * @param channel 채널
 * @param templateCode 템플릿 코드
 * @param subject 제목
 * @param body 본문
 * @param status 상태
 * @param attemptCount 시도 횟수
 * @param lastAttemptedAt 마지막 시도 시각
 * @param createdAt 생성 시각
 */
public record NotificationAdminResponse(
        UUID requestId,
        RecipientType recipientType,
        UUID recipientId,
        String recipientAddress,
        NotificationChannel channel,
        String templateCode,
        String subject,
        String body,
        NotificationStatus status,
        int attemptCount,
        LocalDateTime lastAttemptedAt,
        LocalDateTime createdAt
) {

    public static NotificationAdminResponse from(NotificationRequest req) {
        return new NotificationAdminResponse(
                req.getId(),
                req.getRecipientType(),
                req.getRecipientId(),
                req.getRecipientAddress(),
                req.getChannel(),
                req.getTemplateCode(),
                req.getSubject(),
                req.getBody(),
                req.getStatus(),
                req.getAttemptCount(),
                req.getLastAttemptedAt(),
                req.getCreatedAt());
    }
}
