package com.samhanair.logis.notification.web.dto;

import com.samhanair.logis.notification.domain.NotificationCenter;
import com.samhanair.logis.notification.domain.NotificationSeverity;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationCenterResponse(
        UUID id,
        String channel,
        NotificationSeverity severity,
        String title,
        String body,
        String deeplink,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationCenterResponse from(NotificationCenter n) {
        return new NotificationCenterResponse(
                n.getId(),
                n.getChannel(),
                n.getSeverity(),
                n.getTitle(),
                n.getBody(),
                n.getDeeplink(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
