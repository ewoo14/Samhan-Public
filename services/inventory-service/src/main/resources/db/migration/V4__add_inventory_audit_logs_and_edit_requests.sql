-- V4__add_inventory_audit_logs_and_edit_requests.sql
-- inventory-service — PR-H4b (Phase 12 Step 4b): shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a) = shared:realtime-abstraction module 추출 (slip-service 시범 적용).
--   * 본 단계 (PR-H4b BE-B) = inventory-service + arologis-service 에 shared 모듈 일괄 적용.
--   * shared:realtime-abstraction 의 audit_log_template.sql / edit_request_template.sql 1:1 매핑.
--
-- 도메인 매핑 (inventory-service):
--   * AuditLogEntry @MappedSuperclass 의 entity_id = 변경 대상 entity (InventoryAudit / StockBalance / StockLot 등) 의 UUID
--   * EditRequestRecord @MappedSuperclass 의 entity_id = InventoryAudit UUID (잠금 채널 = COMPLETED 후 audit 본문 수정 요청)
--   * 컬럼명 entity_id 유지 (shared @MappedSuperclass 일관) — getter alias (getInventoryAuditId 등) 는 entity 에서 표면화
--
-- 잠금 정책 (사용자 명시 — D-P12-04b):
--   * InventoryAudit:  PLANNED / IN_PROGRESS = 자유,  COMPLETED = LOCKED_REQUIRES_APPROVAL,  CANCELLED = TERMINAL
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 inventory_audits / inventory_audit_lines / stock_* IT 영향 0
--   * FK 미강제 — 도메인 entity soft delete 후에도 audit/요청 row 보존 (회계 감사)

----------------------------------------------------------------------
-- 1) inventory_audit_logs — 도메인 entity (InventoryAudit/StockBalance/StockLot) 변경 audit overlay
----------------------------------------------------------------------
CREATE TABLE inventory_audit_logs (
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

COMMENT ON TABLE inventory_audit_logs IS
    'PR-H4b inventory 도메인 audit overlay — Phase 12 Step 4b. InventoryAudit/StockBalance/StockLot 변경 1행 + SSE broadcast';

COMMENT ON COLUMN inventory_audit_logs.entity_id IS
    '변경 대상 entity UUID (InventoryAudit/StockBalance/StockLot 등) — FK 미강제 (entity soft delete 후 audit 영구 보존)';

COMMENT ON COLUMN inventory_audit_logs.revision_no IS
    'entity 별 단조 증가 수정 횟수. 같은 mutation 의 다중 필드 변경은 같은 값을 공유';

COMMENT ON COLUMN inventory_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

COMMENT ON COLUMN inventory_audit_logs.field_name IS
    'JSON-path-like 필드 식별자. 헤더 = "auditDate"/"status" 등, 라인 = "lines[idx].actualQty" 등';

-- entity 별 timeline 정렬 인덱스 (FE timeline 표시)
CREATE INDEX ix_inventory_audit_logs_entity_revision
    ON inventory_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

-- revert lookup 인덱스 (특정 revision 의 row 들 조회)
CREATE INDEX ix_inventory_audit_logs_entity_revision_no
    ON inventory_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;

-- 사용자별 활동 통계
CREATE INDEX ix_inventory_audit_logs_actor
    ON inventory_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) inventory_edit_requests — InventoryAudit (COMPLETED 후) 수정/삭제 요청 워크플로우
----------------------------------------------------------------------
CREATE TABLE inventory_edit_requests (
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

COMMENT ON TABLE inventory_edit_requests IS
    'PR-H4b InventoryAudit COMPLETED 후 수정/삭제 요청 워크플로우. MANAGER 수락 1회 소진 후 mutation 가능';

COMMENT ON COLUMN inventory_edit_requests.entity_id IS
    'InventoryAudit UUID (FK 미강제) — COMPLETED 단계의 entity 본문 수정 채널';

COMMENT ON COLUMN inventory_edit_requests.target_role IS
    'WAREHOUSE (창고 직원) | MANAGER (관리자). 회계 감사 대상이라 default MANAGER 정책';

-- mutation 가드용 — entity 별 활성 APPROVED lookup
CREATE INDEX ix_inventory_edit_requests_entity_status
    ON inventory_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

-- 권한자 대시보드 — target_role + PENDING
CREATE INDEX ix_inventory_edit_requests_role_status
    ON inventory_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

-- 스케줄러 자동 만료 — PENDING + expires_at < now
CREATE INDEX ix_inventory_edit_requests_pending_expires
    ON inventory_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
