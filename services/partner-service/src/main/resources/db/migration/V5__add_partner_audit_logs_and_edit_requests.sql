-- V5__add_partner_audit_logs_and_edit_requests.sql
-- Partner Service — PR-H4b (Phase 12 Step 4b): shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a, shared:realtime-abstraction) = 14 service 공통 audit overlay +
--     SSE broker + edit-request lock 추출.
--   * 본 단계 (PR-H4b BE-A) = partner-service 자체 audit_log + edit_request 테이블 신규.
--   * Samhan Public 이식 강조 — audit 자체는 PostgreSQL row, 실시간 sync 는 InMemoryRealtimeBroker.
--
-- 적용 entity:
--   * partner_audit_logs.entity_id = Partner / BlockedPartner UUID. service layer 가 entity_kind
--     별도 인지 (audit 테이블은 통합 보존).
--   * partner_edit_requests.entity_id = 동일.
--
-- 잠금 정책 (사용자 명시 — feedback_role_naming_full):
--   * Partner.status = ACTIVE — 자유 mutation
--   * Partner.status = SUSPENDED — 자유 (재개 가능 단계)
--   * Partner.status = TERMINATED — 영구 잠금 (계약 해지)
--   * BlockedPartner = LOCKED_REQUIRES_APPROVAL (BLOCKED 상태 잠금 — MANAGER 수락 필요)
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 partners / blocked_partners / partner_credit_history IT 영향 0
--   * FK 미강제 — 도메인 entity soft-delete 후에도 audit row 보존

CREATE TABLE partner_audit_logs (
    id              UUID         PRIMARY KEY,
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

COMMENT ON TABLE partner_audit_logs IS
    'PR-H4b partner audit overlay — Partner / BlockedPartner 본문 수정 1행 + SSE broadcast';

COMMENT ON COLUMN partner_audit_logs.entity_id IS
    '대상 entity UUID (Partner / BlockedPartner). FK 미강제 — soft-delete 후에도 보존';

COMMENT ON COLUMN partner_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자';

CREATE INDEX ix_partner_audit_logs_entity_revision
    ON partner_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_audit_logs_actor
    ON partner_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

-- ============================================================
-- partner_edit_requests — 잠금 entity (BlockedPartner) mutation 해제 채널
-- ============================================================

CREATE TABLE partner_edit_requests (
    id              UUID         PRIMARY KEY,
    entity_id       UUID         NOT NULL,
    requester_id    UUID         NOT NULL,
    requester_name  VARCHAR(50)  NOT NULL,
    request_type    VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    target_role     VARCHAR(20)  NOT NULL,
    decided_by_id   UUID,
    decided_by_name VARCHAR(50),
    decided_at      TIMESTAMP,
    decision_reason VARCHAR(500),
    requested_at    TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE partner_edit_requests IS
    'PR-H4b partner edit-request — BlockedPartner 잠금 mutation 해제 채널 (관리자 수락 필요)';

COMMENT ON COLUMN partner_edit_requests.target_role IS
    'WAREHOUSE / MANAGER (partner 도메인은 MANAGER 우선)';

CREATE INDEX ix_partner_edit_requests_entity_status
    ON partner_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_edit_requests_role_status
    ON partner_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_edit_requests_pending_expires
    ON partner_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
