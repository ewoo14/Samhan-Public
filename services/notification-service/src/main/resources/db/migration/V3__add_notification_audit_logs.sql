-- V3__add_notification_audit_logs.sql
-- Notification Service — PR-H4b (Phase 12 Step 4b): 통합 audit overlay (lock 불필요).
--
-- 컨텍스트:
--   * PR-H4a (Phase 12 Step 4a) = shared:realtime-abstraction 모듈 추출 + slip-service 시범 마이그.
--   * 본 PR-H4b = 13 service 일괄 적용 (BE-D 분담분 = user / dc-config / notification 3 service).
--   * shared/realtime-abstraction/src/main/resources/db/template/audit_log_template.sql 1:1 복제 후
--     도메인 prefix `notification_` 적용. AuditLogEntry @MappedSuperclass 9 필드 + BaseEntity 7 audit.
--
-- 도입 사유 (BE-D 분담 정책):
--   * ChatRoomMapping (PartnerChatRoomMapping V2) — 거래처 단톡방 매핑 변경 audit
--     (담당자 인계 추적, 발송 오류 책임 추적).
--   * BlockedPartner — 발송 차단 거래처 변경 audit (수신 거부 추적).
--   * Notification 발송 이력 audit (NotificationLog) — 발송 성공/실패 추적.
--     append-only — lock 불필요 (요청 후 발송 결과는 외부 vendor 응답 즉시 기록).
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 notification_requests / notification_logs / partner_chat_room_mappings IT 영향 0.
--   * FK 미강제 — 매핑/요청 soft-delete 후에도 audit row 보존 (분쟁 대응).
--
-- edit-request 미도입:
--   * notification 도메인은 모두 append-only 또는 마스터 데이터 (단톡방 매핑) — lock 불필요.
--   * Designer H4b-be-rollout-checklist § 1.1 = "notification = broker only".
--   * 사용자 task 명시: "Notification 발송 이력 audit (lock 불필요 — append-only)".

CREATE TABLE notification_audit_logs (
    id              UUID         PRIMARY KEY,

    -- AuditLogEntry 9 필드
    entity_id       UUID         NOT NULL,
    revision_no     INT          NOT NULL,
    actor_id        UUID         NOT NULL,
    actor_name      VARCHAR(50)  NOT NULL,
    actor_color     VARCHAR(20),
    field_name      VARCHAR(50)  NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_at      TIMESTAMP    NOT NULL,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE notification_audit_logs IS
    'PR-H4b notification-service audit overlay — PartnerChatRoomMapping/BlockedPartner '
    '변경 + NotificationLog 발송 결과 audit (append-only, lock 불필요).';

COMMENT ON COLUMN notification_audit_logs.entity_id IS
    'PartnerChatRoomMapping.id 또는 BlockedPartner.id 또는 NotificationLog.id (FK 미강제).';

COMMENT ON COLUMN notification_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자.';

COMMENT ON COLUMN notification_audit_logs.field_name IS
    'PartnerChatRoomMapping: chatRoomId/managerEmployeeId/source. '
    'BlockedPartner: blockReason/blockedAt. '
    'NotificationLog: status/vendorResponseCode/vendorMessageId/sentAt.';

CREATE INDEX ix_notification_audit_logs_entity_revision
    ON notification_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_notification_audit_logs_entity_revision_no
    ON notification_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;
