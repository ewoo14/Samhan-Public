package com.samhanair.logis.log.messaging;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire-format audit log event published by other services to RabbitMQ
 * exchange {@code samhan.audit.exchange} with routing key {@code audit.<...>}.
 *
 * Mirrors {@link com.samhanair.logis.log.domain.AuditLog} minus
 * {@code ingestedAt} (set on the consumer side at write time).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogEvent(
        String id,
        String serviceName,
        String userId,
        String userRole,
        String action,
        String resourceType,
        String resourceId,
        String description,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {
}
