-- V2__add_groupware_audit_logs.sql
-- Groupware Service — PR-H4b (Phase 12 Step 4b) BE-E: shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a) = shared:realtime-abstraction module 추출 + slip-service 시범 활용.
--   * 본 단계 (PR-H4b BE-E) = groupware-service 에 audit_log 테이블 적용. 향후 ApprovalLine / Message
--     / Schedule 등 도메인 entity 변경 시점에 본 테이블에 row 1건 (필드별 diff) 기록 가능.
--   * 본 PR 범위는 schema + entity + repository 까지 (실 mutation 호출자 통합은 향후 PR).
--
-- 스키마 = shared:realtime-abstraction `db/template/audit_log_template.sql` 일관.
-- 컬럼명 entity_id 유지 (AuditLogEntry @MappedSuperclass 와 매핑) — 의미는 ApprovalLine.id /
-- Message.id / Schedule.id 등 도메인 entity UUID.
--
-- 컬럼 컨벤션 (BaseEntity 7 + AuditLogEntry 9):
--   * id UUID PK
--   * entity_id UUID NOT NULL — 소속 도메인 entity FK (FK 미강제, soft-delete 후 보존)
--   * revision_no INT NOT NULL — 단조 증가 수정 횟수
--   * actor_id UUID NOT NULL — 수정자 UUID (audit/감사 추적용, 사용자 화면 노출 금지)
--   * actor_name VARCHAR(50) NOT NULL — 수정자 표시명 (UUID 비공개 가드)
--   * actor_color VARCHAR(20) — HSL hex (FE 색상 backup, optional)
--   * field_name VARCHAR(50) NOT NULL — 'title' / 'content' / 'startDate' 등
--   * old_value TEXT — 이전 값 (취소선 표시용, NULL 허용)
--   * new_value TEXT — 새 값 (NULL 허용)
--   * changed_at TIMESTAMP NOT NULL — 변경 시각 (revert 정렬 + 인쇄 양식)
--   * BaseEntity 7: created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted

CREATE TABLE groupware_audit_logs (
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

    -- BaseEntity 7 audit 필드
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE groupware_audit_logs IS
    'PR-H4b BE-E groupware 도메인 entity (ApprovalLine/Message/Schedule) 변경 audit overlay';

COMMENT ON COLUMN groupware_audit_logs.entity_id IS
    '소속 도메인 entity FK (ApprovalLine.id / Message.id / Schedule.id 등). FK 미강제 — 도메인 soft-delete 후에도 audit 보존';

COMMENT ON COLUMN groupware_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

-- entity 별 audit 조회 + 최신 revision 우선 인덱스 (FE timeline 표시)
CREATE INDEX ix_groupware_audit_logs_entity_revision
    ON groupware_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

-- 사용자별 audit 조회 (감사 추적 / 활동 통계)
CREATE INDEX ix_groupware_audit_logs_actor
    ON groupware_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;
