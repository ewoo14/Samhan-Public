package com.samhanair.logis.notification.web.dto;

import com.samhanair.logis.notification.domain.NotificationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code /internal/notifications} 발송 요청 body — source service 가 호출.
 *
 * @param channel        알림 채널 키 ({@code SAFETY_STOCK} / {@code MESSENGER} / {@code APPROVAL} 등)
 * @param severity       심각도 (INFO/WARNING/CRITICAL)
 * @param title          알림 제목 (200자 이내)
 * @param body           본문 (TEXT)
 * @param targetRole     role CSV (예: {@code "MASTER,MANAGER"}), null/blank 면 role 필터 미적용
 * @param targetUserId   특정 사용자 UUID, null 면 role 기반
 * @param sourceService  발송 service 명 (기록용)
 * @param sourceRefId    source 식별자 (예: productId+warehouseId, messageId)
 * @param deeplink       FE 가 클릭 시 이동할 라우트
 */
public record NotificationPublishRequest(
        @NotBlank String channel,
        @NotNull NotificationSeverity severity,
        @NotBlank String title,
        String body,
        String targetRole,
        UUID targetUserId,
        @NotBlank String sourceService,
        String sourceRefId,
        String deeplink
) {
}
