-- ============================================================
-- shared:realtime-abstraction — edit_request 테이블 template
-- PR-H4a (Phase 12 Step 4a) — 14 service 공통 참조용.
-- ============================================================
--
-- 본 파일은 직접 적용되지 않습니다. 각 service 가 자체 Flyway V?? 파일에서 본 schema 를 복사하여
-- 도메인별 테이블명 (slip_edit_requests / lot_edit_requests / dispatch_edit_requests / ...) 으로 적용합니다.
--
-- 적용 예 (slip-service V19__add_slip_edit_requests.sql 참조):
--   1) 본 파일 복사
--   2) 테이블명 변경: <DOMAIN>_edit_requests
--   3) 컬럼 entity_id 의미 주석 변경 (slip_id / lot_id 등) — 컬럼명은 entity_id 유지
--   4) FK 미강제 (도메인 entity soft-delete 후에도 row 보존)
--
-- 컬럼 = EditRequestRecord @MappedSuperclass 의 13 필드 + BaseEntity 7 필드.

CREATE TABLE IF NOT EXISTS <DOMAIN>_edit_requests (
    id                  UUID PRIMARY KEY,

    -- EditRequestRecord 13 필드
    entity_id           UUID NOT NULL,
    requester_id        UUID NOT NULL,
    requester_name      VARCHAR(50) NOT NULL,
    request_type        VARCHAR(20) NOT NULL,       -- EDIT/DELETE
    reason              VARCHAR(500),
    status              VARCHAR(20) NOT NULL,       -- PENDING/APPROVED/REJECTED/EXPIRED
    target_role         VARCHAR(20) NOT NULL,       -- WAREHOUSE/MANAGER
    decided_by_id       UUID,
    decided_by_name     VARCHAR(50),
    decided_at          TIMESTAMP,
    decision_reason     VARCHAR(500),
    requested_at        TIMESTAMP NOT NULL,
    expires_at          TIMESTAMP,

    -- BaseEntity 7 audit 필드
    created_at          TIMESTAMP NOT NULL,
    created_by          VARCHAR(50) NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);

-- mutation 가드용 — entity_id + APPROVED + 활성 (소진 전) lookup
CREATE INDEX IF NOT EXISTS ix_<DOMAIN>_edit_requests_entity_status
    ON <DOMAIN>_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

-- 권한자 대시보드 — target_role + PENDING
CREATE INDEX IF NOT EXISTS ix_<DOMAIN>_edit_requests_role_status
    ON <DOMAIN>_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

-- 스케줄러 자동 만료 — PENDING + expires_at < now
CREATE INDEX IF NOT EXISTS ix_<DOMAIN>_edit_requests_pending_expires
    ON <DOMAIN>_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
