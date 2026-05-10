-- V3__add_dc_config_audit_logs_and_edit_requests.sql
-- DC Config Service — PR-H4b (Phase 12 Step 4b): DcConfig 변경 audit + 수정 요청 워크플로우.
--
-- 컨텍스트:
--   * PR-H4a (Phase 12 Step 4a) = shared:realtime-abstraction 모듈 추출 + slip-service 시범 마이그.
--   * 본 PR-H4b = 13 service 일괄 적용 (BE-D 분담분 = user / dc-config / notification 3 service).
--   * shared/realtime-abstraction/src/main/resources/db/template/audit_log_template.sql 1:1 복제 후
--     도메인 prefix `dc_config_` 적용. AuditLogEntry @MappedSuperclass 9 필드 + BaseEntity 7 audit.
--
-- 도입 사유:
--   * DcConfig — 거래처별 할인율/옵션 정액 DC/단위 반올림 16종 CFG_RAW 변경 audit 의무
--     (DC 정책 변경 추적 의무 — 가격 산정 분쟁 대응).
--   * EditLockGuard 정책: DcConfig 는 단순 entity (status 컬럼 없음) — 본 service 는
--     "현재 적용 중인 정책 (in-use)" 판정을 source = SAMHAN_PUBLIC_SEED 등으로 분류.
--     단순화: 모든 DcConfig 변경에 audit overlay 적용, edit-request 는 정책 적용 후 신중을 위해 도입
--     (target_role = MANAGER, source 가 SAMHAN_PUBLIC_SEED/INVENTORY_IMPORT 일 때 잠금).
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 dc_configs / dc_rules / partners IT 영향 0.
--   * FK 미강제 — DcConfig soft-delete 후에도 audit row 보존 (분쟁 대응).

CREATE TABLE dc_config_audit_logs (
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

COMMENT ON TABLE dc_config_audit_logs IS
    'PR-H4b dc-config-service audit overlay — DcConfig 16종 CFG_RAW 변경 1건당 필드별 1행.';

COMMENT ON COLUMN dc_config_audit_logs.entity_id IS
    'DcConfig.id 또는 DcRule.id (FK 미강제, soft-delete 후 보존).';

COMMENT ON COLUMN dc_config_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자.';

COMMENT ON COLUMN dc_config_audit_logs.field_name IS
    'DcConfig: homeDiscountRate/commercialDiscountRate/showIHose/discount360Amount/'
    'discount4WayAmount/discount1WayAmount/discountStandAmount/discountDeluxeAmount/'
    'discountFirstGradeAmount/unitRoundTo/unitRoundMode/unitProcessingEnabled/source/note.';

CREATE INDEX ix_dc_config_audit_logs_entity_revision
    ON dc_config_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_dc_config_audit_logs_entity_revision_no
    ON dc_config_audit_logs (entity_id, revision_no)
    WHERE is_deleted = FALSE;

-- ============================================================
-- dc_config_edit_requests (DcConfig 수정/삭제 요청 워크플로우)
-- ============================================================
CREATE TABLE dc_config_edit_requests (
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

COMMENT ON TABLE dc_config_edit_requests IS
    'PR-H4b dc-config-service 수정/삭제 요청 워크플로우 — 정책 적용 후 변경 신중을 위한 채널. '
    'target_role = MANAGER (DC 정책 권한 그룹).';

CREATE INDEX ix_dc_config_edit_requests_entity_status
    ON dc_config_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_dc_config_edit_requests_role_status
    ON dc_config_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_dc_config_edit_requests_pending_expires
    ON dc_config_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
