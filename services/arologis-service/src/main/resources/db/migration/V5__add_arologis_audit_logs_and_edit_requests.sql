-- V5__add_arologis_audit_logs_and_edit_requests.sql
-- arologis-service — PR-H4b (Phase 12 Step 4b): shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a) = shared:realtime-abstraction module 추출 (slip-service 시범 적용).
--   * 본 단계 (PR-H4b BE-B) = inventory-service + arologis-service 에 shared 모듈 일괄 적용.
--   * shared:realtime-abstraction 의 audit_log_template.sql / edit_request_template.sql 1:1 매핑.
--
-- 도메인 매핑 (arologis-service):
--   * AuditLogEntry @MappedSuperclass 의 entity_id = 변경 대상 entity (Dispatch / VehicleStop) 의 UUID
--   * EditRequestRecord @MappedSuperclass 의 entity_id = Dispatch UUID (잠금 채널 = DISPATCHED/DELIVERED 후 본문 수정)
--
-- 잠금 정책 (사용자 명시 — D-P12-04b):
--   * Dispatch:
--       PLANNED (StopStatus 모두 PENDING/UNPARSED) = 자유,
--       DISPATCHED (어떤 stop 이 ARRIVED/DELIVERED) = LOCKED_REQUIRES_APPROVAL,
--       DELIVERED (모든 stop 이 DELIVERED/FAILED) = LOCKED_REQUIRES_APPROVAL
--   (Dispatch 도메인 자체는 status enum 보유 X — Dispatch 의 derived status 는 stop 들 aggregate 로 산출)
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 dispatches / vehicle_stops / drivers IT 영향 0
--   * FK 미강제 — 도메인 entity soft delete 후에도 audit/요청 row 보존

----------------------------------------------------------------------
-- 1) arologis_audit_logs — 도메인 entity (Dispatch/VehicleStop) 변경 audit overlay
----------------------------------------------------------------------
CREATE TABLE arologis_audit_logs (
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

COMMENT ON TABLE arologis_audit_logs IS
    'PR-H4b arologis 도메인 audit overlay — Phase 12 Step 4b. Dispatch/VehicleStop 변경 1행 + SSE broadcast';

COMMENT ON COLUMN arologis_audit_logs.entity_id IS
    '변경 대상 entity UUID (Dispatch/VehicleStop) — FK 미강제 (entity soft delete 후 audit 영구 보존)';

COMMENT ON COLUMN arologis_audit_logs.revision_no IS
    'entity 별 단조 증가 수정 횟수. 같은 mutation 의 다중 필드 변경은 같은 값을 공유';

COMMENT ON COLUMN arologis_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

COMMENT ON COLUMN arologis_audit_logs.field_name IS
    'JSON-path-like 필드 식별자. 예: "stops[2].status" / "dispatchType" / "assignedDriverId"';

CREATE INDEX ix_arologis_audit_logs_entity_revision
    ON arologis_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_arologis_audit_logs_entity_revision_no
    ON arologis_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_arologis_audit_logs_actor
    ON arologis_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) arologis_edit_requests — Dispatch (DISPATCHED/DELIVERED 후) 수정/삭제 요청 워크플로우
----------------------------------------------------------------------
CREATE TABLE arologis_edit_requests (
    id                  UUID         PRIMARY KEY,

    -- EditRequestRecord 13 필드
    entity_id           UUID         NOT NULL,
    requester_id        UUID         NOT NULL,
    requester_name      VARCHAR(50)  NOT NULL,
    request_type        VARCHAR(20)  NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    target_role         VARCHAR(20)  NOT NULL,
    decided_by_id       UUID,
    decided_by_name     VARCHAR(50),
    decided_at          TIMESTAMP,
    decision_reason     VARCHAR(500),
    requested_at        TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP,

    -- BaseEntity 7 audit
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE arologis_edit_requests IS
    'PR-H4b Dispatch DISPATCHED/DELIVERED 후 수정/삭제 요청 워크플로우. MANAGER 수락 1회 소진 후 mutation';

COMMENT ON COLUMN arologis_edit_requests.entity_id IS
    'Dispatch UUID (FK 미강제) — DISPATCHED/DELIVERED derived status 단계 본문 수정 채널';

COMMENT ON COLUMN arologis_edit_requests.target_role IS
    'WAREHOUSE (창고 직원) | MANAGER (관리자/배차담당). 본 도메인 default MANAGER';

CREATE INDEX ix_arologis_edit_requests_entity_status
    ON arologis_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_arologis_edit_requests_role_status
    ON arologis_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_arologis_edit_requests_pending_expires
    ON arologis_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
