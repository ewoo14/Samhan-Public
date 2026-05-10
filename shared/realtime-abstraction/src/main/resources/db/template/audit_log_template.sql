-- ============================================================
-- shared:realtime-abstraction — audit_log 테이블 template
-- PR-H4a (Phase 12 Step 4a) — 14 service 공통 참조용.
-- ============================================================
--
-- 본 파일은 직접 적용되지 않습니다. 각 service 가 자체 Flyway V?? 파일에서 본 schema 를 복사하여
-- 도메인별 테이블명 (slip_audit_logs / lot_audit_logs / dispatch_audit_logs / ...) 으로 적용합니다.
--
-- 적용 예 (slip-service V18__add_slip_audit_logs.sql 참조):
--   1) 본 파일 복사
--   2) 테이블명 변경: <DOMAIN>_audit_logs (예: slip_audit_logs, lot_audit_logs)
--   3) 컬럼 entity_id 의미 주석 변경 (slip_id / lot_id / dispatch_id 등) — 컬럼명은 entity_id 유지
--      (AuditLogEntry @MappedSuperclass 와 일관)
--   4) FK 미강제 (도메인 entity soft-delete 후에도 audit row 보존)
--
-- 컬럼 = AuditLogEntry @MappedSuperclass 의 9 필드 + BaseEntity 7 필드.

CREATE TABLE IF NOT EXISTS <DOMAIN>_audit_logs (
    id              UUID PRIMARY KEY,

    -- AuditLogEntry 9 필드
    entity_id       UUID NOT NULL,                  -- 소속 도메인 entity FK (FK 미강제)
    revision_no     INT NOT NULL,                   -- 단조 증가 수정 횟수
    actor_id        UUID NOT NULL,                  -- 수정자 UUID (audit/감사용)
    actor_name      VARCHAR(50) NOT NULL,           -- 수정자 표시명 (UUID 비공개 가드)
    actor_color     VARCHAR(20),                    -- FE 색상 hex (선택)
    field_name      VARCHAR(50) NOT NULL,           -- 변경된 필드 식별자
    old_value       TEXT,                           -- 이전 값 (선택)
    new_value       TEXT,                           -- 새 값 (선택, old/new 둘 다 null 거부)
    changed_at      TIMESTAMP NOT NULL,             -- 변경 시각 (revert 정렬 + 인쇄)

    -- BaseEntity 7 audit 필드
    created_at      TIMESTAMP NOT NULL,
    created_by      VARCHAR(50) NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

-- FE timeline 정렬 인덱스 — entity_id + revision_no DESC + changed_at DESC
CREATE INDEX IF NOT EXISTS ix_<DOMAIN>_audit_logs_entity_revision
    ON <DOMAIN>_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

-- revert 시 특정 revision lookup 인덱스
CREATE INDEX IF NOT EXISTS ix_<DOMAIN>_audit_logs_entity_revision_no
    ON <DOMAIN>_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;
