-- V4__add_user_audit_logs_and_edit_requests.sql
-- User Service — PR-H4b (Phase 12 Step 4b): Employee/Department audit overlay + 수정 요청 워크플로우.
--
-- 컨텍스트:
--   * PR-H4a (Phase 12 Step 4a) = shared:realtime-abstraction 모듈 추출 + slip-service 시범 마이그.
--   * 본 PR-H4b = 13 service 일괄 적용 (BE-D 분담분 = user / dc-config / notification 3 service).
--   * shared/realtime-abstraction/src/main/resources/db/template/audit_log_template.sql 1:1 복제 후
--     도메인 prefix `user_` 적용. AuditLogEntry @MappedSuperclass 9 필드 + BaseEntity 7 audit + Soft Delete.
--
-- 도입 사유 (BE-D 분담 정책):
--   * Employee — 이름/연락처/소속 부서/직책/이메일/전화 등 마스터 데이터 변경 audit 의무
--     (HR 정책 + 법적 인사 기록 보존).
--   * Department — 부서 신규/이전/통폐합 audit 의무 (조직도 변경 추적).
--   * EditLockGuard 정책: terminationDate != null = DEACTIVATED 단계 = 잠금
--     → 활성 APPROVED 요청 1건 소진 후 mutation 가능 (재고용 / 정정 등 별도 채널).
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 employees / departments / role_change_history IT 영향 0.
--   * FK 미강제 — Employee soft-delete 후에도 audit row 보존 (인사 기록 영구 보존).

CREATE TABLE user_audit_logs (
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

COMMENT ON TABLE user_audit_logs IS
    'PR-H4b user-service audit overlay — Employee/Department 변경 1건당 필드별 1행 (HR 인사 기록).';

COMMENT ON COLUMN user_audit_logs.entity_id IS
    'Employee.id 또는 Department.id (FK 미강제, soft-delete 후 보존).';

COMMENT ON COLUMN user_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리.';

COMMENT ON COLUMN user_audit_logs.field_name IS
    'Employee: fullName/position/email/phone/department/teamLead/roleSnapshot/terminationDate. '
    'Department: name/managerId/parentId.';

CREATE INDEX ix_user_audit_logs_entity_revision
    ON user_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_user_audit_logs_entity_revision_no
    ON user_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;

-- ============================================================
-- user_edit_requests (Employee/Department 수정/삭제 요청 워크플로우)
-- ============================================================
CREATE TABLE user_edit_requests (
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

COMMENT ON TABLE user_edit_requests IS
    'PR-H4b user-service 수정/삭제 요청 워크플로우 — DEACTIVATED 직원 (terminationDate != null) '
    '의 mutation 잠금 해제 채널. target_role = MANAGER (인사 권한 그룹).';

CREATE INDEX ix_user_edit_requests_entity_status
    ON user_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_user_edit_requests_role_status
    ON user_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_user_edit_requests_pending_expires
    ON user_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
