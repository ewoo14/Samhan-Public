-- V3__add_realtime_overlay.sql
-- partner-order-service — PR-H4b (Phase 12 Step 4b BE-C): shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a) = shared:realtime-abstraction module 추출 + slip-service 시범.
--   * 본 단계 (PR-H4b BE-C) = partner-order-service + product-service 에 동일 패턴 적용.
--   * 도메인:
--       - PartnerOrder (확정 주문 헤더) — 본 service 의 mutation 잠금 대상.
--       - VendorOrder = 본 service 의 OCR + parser 흐름 결과로 PartnerOrder 등록 (별도 entity 없음).
--         따라서 audit / edit-request 도메인은 PartnerOrder 1종이며 entity_id = partner_orders.id.
--   * Samhan Public 이식 강조 — 외부 vendor 의존 0 (audit 자체는 PostgreSQL row, 실시간 sync 는
--     shared module InMemoryRealtimeBroker 재사용).
--
-- 컬럼 컨벤션 (shared:realtime-abstraction AuditLogEntry / EditRequestRecord 기반):
--   * AuditLogEntry: entity_id / revision_no / actor_id / actor_name / actor_color / field_name /
--     old_value / new_value / changed_at + BaseEntity 7
--   * EditRequestRecord: entity_id / requester_id / requester_name / request_type / reason / status /
--     target_role / decided_by_id / decided_by_name / decided_at / decision_reason / requested_at /
--     expires_at + BaseEntity 7
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 partner_orders / partner_order_lines / partner_order_history IT 영향 0
--   * partner_orders.revision_count 신규 컬럼 — DEFAULT 0, 기존 row 자동 backfill (NOT NULL 안전)
--   * FK 미강제 — partner_orders soft delete 후에도 audit / edit-request row 보존 (회계 감사)

----------------------------------------------------------------------
-- 1) partner_order_audit_logs — 주문 본문 수정 audit overlay (PR-H4b)
----------------------------------------------------------------------
CREATE TABLE partner_order_audit_logs (
    id              UUID         PRIMARY KEY,
    entity_id       UUID         NOT NULL,    -- PartnerOrder.id (FK 미강제)
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

COMMENT ON TABLE partner_order_audit_logs IS
    'PR-H4b 거래처 주문 본문 수정 audit overlay — Phase 12 Step 4b BE-C. 필드별 diff 1행 기록 + SSE broadcast';

COMMENT ON COLUMN partner_order_audit_logs.entity_id IS
    'PartnerOrder.id (FK 미강제). soft-delete 된 주문도 audit 영구 보존';

COMMENT ON COLUMN partner_order_audit_logs.revision_no IS
    '주문별 단조 증가 수정 횟수 (partner_orders.revision_count 와 동기화)';

COMMENT ON COLUMN partner_order_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

-- 주문별 audit 조회 + 최신 revision 우선 인덱스 (FE timeline 표시)
CREATE INDEX ix_partner_order_audit_logs_entity
    ON partner_order_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

-- 사용자별 audit 조회 (감사 추적 / 활동 통계)
CREATE INDEX ix_partner_order_audit_logs_actor
    ON partner_order_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

-- partner_orders.revision_count — 누적 수정 횟수 (다음 revision_no 채번용 + FE 표시).
ALTER TABLE partner_orders ADD COLUMN revision_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN partner_orders.revision_count IS
    'PR-H4b 누적 수정 횟수 — partner_order_audit_logs 의 다음 revision_no 채번 보조 + FE timeline UI 표시';

----------------------------------------------------------------------
-- 2) partner_order_edit_requests — 수정/삭제 요청 워크플로우 (PR-H4b)
----------------------------------------------------------------------
-- 사용자 명시 잠금 정책 (개발책임자 결정 — 거래처 주문 도메인):
--   * DRAFT/CONFIRMING — 작성자 자유 mutation (본 도메인 사용 X).
--   * CONFIRMED (slip 발행 후) — 작성자 직접 차단 → 본 channel 요청 → MANAGER 수락 시 1회 mutation 가능.
--   * CANCELED — 종결됨, 요청 의미 없음.

CREATE TABLE partner_order_edit_requests (
    id              UUID         PRIMARY KEY,
    entity_id       UUID         NOT NULL,    -- PartnerOrder.id (FK 미강제)
    requester_id    UUID         NOT NULL,
    requester_name  VARCHAR(50)  NOT NULL,
    request_type    VARCHAR(20)  NOT NULL,    -- EDIT | DELETE
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | EXPIRED
    target_role     VARCHAR(20)  NOT NULL,    -- WAREHOUSE | MANAGER (거래처 주문은 MANAGER 기본)
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

COMMENT ON TABLE partner_order_edit_requests IS
    'PR-H4b 거래처 주문 수정/삭제 요청 워크플로우 — Phase 12 Step 4b BE-C. CONFIRMED 주문의 mutation 잠금 해제 채널';

COMMENT ON COLUMN partner_order_edit_requests.request_type IS
    'EDIT (헤더/라인/필드 수정 요청) | DELETE (주문 자체 삭제 요청)';

COMMENT ON COLUMN partner_order_edit_requests.status IS
    'PENDING (요청 직후) → APPROVED (수락, 1회 mutation 가능) | REJECTED (거절) | EXPIRED (자동 만료)';

COMMENT ON COLUMN partner_order_edit_requests.target_role IS
    'WAREHOUSE | MANAGER. 거래처 주문 도메인 default = MANAGER (admin 결정 권한)';

-- 주문별 활성 요청 조회 (mutation 가드 — APPROVED 1건 있어야 진행)
CREATE INDEX ix_partner_order_edit_requests_entity_status
    ON partner_order_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

-- 권한자 그룹 대시보드 (MANAGER PENDING 목록)
CREATE INDEX ix_partner_order_edit_requests_target_role
    ON partner_order_edit_requests (target_role, status)
    WHERE is_deleted = FALSE;

-- PENDING + expires_at 인덱스 (스케줄러 만료 처리 효율)
CREATE INDEX ix_partner_order_edit_requests_pending_expires
    ON partner_order_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING';
