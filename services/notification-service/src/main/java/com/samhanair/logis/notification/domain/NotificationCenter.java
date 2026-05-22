package com.samhanair.logis.notification.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 통합 알림 (Issue 4 Slice 1).
 *
 * <p>NotificationLog (게이트웨이 발송 이력) 와 별개의 도메인 — 사용자 화면 알림.
 * target_role PostgreSQL TEXT[] + target_user_id UUID 조합으로 노출 대상 결정.
 * read_at NULL = 미확인, NOT NULL = acknowledge 시점.
 */
@Entity
@Getter
@Table(name = "notification_center")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class NotificationCenter extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "channel", nullable = false, length = 32, updatable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16, updatable = false)
    private NotificationSeverity severity;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT", updatable = false)
    private String body;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_role", columnDefinition = "TEXT[]", updatable = false)
    private String[] targetRole;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "source_service", nullable = false, length = 64, updatable = false)
    private String sourceService;

    @Column(name = "source_ref_id", length = 200, updatable = false)
    private String sourceRefId;

    @Column(name = "deeplink", length = 500, updatable = false)
    private String deeplink;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static NotificationCenter publish(String channel, NotificationSeverity severity,
                                             String title, String body,
                                             List<String> targetRole, UUID targetUserId,
                                             String sourceService, String sourceRefId,
                                             String deeplink) {
        NotificationCenter n = new NotificationCenter();
        n.channel = channel;
        n.severity = severity;
        n.title = title;
        n.body = body;
        n.targetRole = normalizeTargetRole(targetRole);
        n.targetUserId = targetUserId;
        n.sourceService = sourceService;
        n.sourceRefId = sourceRefId;
        n.deeplink = deeplink;
        return n;
    }

    public void acknowledge(LocalDateTime when) {
        if (this.readAt == null) {
            this.readAt = when;
        }
    }

    public String[] getTargetRole() {
        return targetRole == null ? null : targetRole.clone();
    }

    private static String[] normalizeTargetRole(List<String> targetRole) {
        if (targetRole == null) {
            return null;
        }
        String[] roles = targetRole.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toArray(String[]::new);
        return roles.length == 0 ? null : roles;
    }
}
