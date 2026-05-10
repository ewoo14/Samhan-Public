package com.samhanair.logis.notification.audit.domain;

import com.samhanair.logis.shared.realtime.audit.AuditLogEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * notification-service audit overlay log — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@code PartnerChatRoomMapping} / {@code BlockedPartner} 변경 + {@code NotificationLog}
 * 발송 결과 audit. shared module 의 {@link AuditLogEntry} {@code @MappedSuperclass} 상속.
 *
 * <p><b>append-only — lock 불필요</b>: 사용자 task 명시 — Notification 발송 이력은 외부 vendor
 * 응답 즉시 1회 기록 후 변경 없음. 매핑/차단 변경도 audit 만 보존하고 수정 자체는 자유 (관리자 권한).
 *
 * <p>Designer H4b-be-rollout-checklist § 1.1 = "notification = broker only" 권고였으나 사용자
 * task 가 audit overlay 명시 도입 → audit 만 (edit-request 미도입).
 */
@Entity
@Getter
@Table(name = "notification_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class NotificationAuditLog extends AuditLogEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private NotificationAuditLog(UUID entityId, int revisionNo, UUID actorId, String actorName,
                                 String actorColor, String fieldName, String oldValue, String newValue) {
        init(entityId, revisionNo, actorId, actorName, actorColor, fieldName, oldValue, newValue, null);
    }

    /** 신규 audit log 정적 factory. */
    public static NotificationAuditLog record(UUID entityId, int revisionNo, UUID actorId, String actorName,
                                              String actorColor, String fieldName,
                                              String oldValue, String newValue) {
        return new NotificationAuditLog(entityId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue);
    }
}
