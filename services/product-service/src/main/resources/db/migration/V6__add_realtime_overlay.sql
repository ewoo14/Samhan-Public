-- V6__add_realtime_overlay.sql
-- product-service — PR-H4b (Phase 12 Step 4b BE-C): shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * Phase 12 Step 4a (PR-H4a) = shared:realtime-abstraction module 추출 + slip-service 시범.
--   * 본 단계 (PR-H4b BE-C) = product-service + partner-order-service 에 동일 패턴 적용.
--   * 도메인:
--       - Product (제품 마스터) — 본 service 의 mutation 잠금 대상.
--       - 잠금 정책: status = ACTIVE → 자유 mutation; DISCONTINUED → APPROVED 1회 소진 후 가능
--         (단종된 제품의 가격/태그 변경은 admin 결정 채널 경유 필수).
--   * Samhan Public 이식 강조 — 외부 vendor 의존 0 (audit 자체는 PostgreSQL row, 실시간 sync 는
--     shared module InMemoryRealtimeBroker 재사용).
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 products / categories / product_specs IT 영향 0
--   * products.revision_count 신규 컬럼 — DEFAULT 0, 기존 row 자동 backfill (NOT NULL 안전)
--   * FK 미강제 — products soft delete 후에도 audit / edit-request row 보존 (회계 감사)

----------------------------------------------------------------------
-- 1) product_audit_logs — 제품 마스터 수정 audit overlay (PR-H4b)
----------------------------------------------------------------------
CREATE TABLE product_audit_logs (
    id              UUID         PRIMARY KEY,
    entity_id       UUID         NOT NULL,    -- Product.id (FK 미강제)
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

COMMENT ON TABLE product_audit_logs IS
    'PR-H4b 제품 마스터 수정 audit overlay — Phase 12 Step 4b BE-C. 필드별 diff 1행 기록 + SSE broadcast';

COMMENT ON COLUMN product_audit_logs.entity_id IS
    'Product.id (FK 미강제). soft-delete 된 제품도 audit 영구 보존';

COMMENT ON COLUMN product_audit_logs.revision_no IS
    '제품별 단조 증가 수정 횟수 (products.revision_count 와 동기화)';

COMMENT ON COLUMN product_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

CREATE INDEX ix_product_audit_logs_entity
    ON product_audit_logs (entity_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_product_audit_logs_actor
    ON product_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

ALTER TABLE products ADD COLUMN revision_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN products.revision_count IS
    'PR-H4b 누적 수정 횟수 — product_audit_logs 의 다음 revision_no 채번 보조 + FE timeline UI 표시';

----------------------------------------------------------------------
-- 2) product_edit_requests — 수정/삭제 요청 워크플로우 (PR-H4b)
----------------------------------------------------------------------
-- 사용자 명시 잠금 정책 (개발책임자 결정 — 제품 마스터 도메인):
--   * ACTIVE — 자유 mutation (본 도메인 사용 X — admin 직접 가능).
--   * DISCONTINUED (단종 처리 후) — 작성자 직접 차단 → 본 channel 요청 → MANAGER 수락 시 1회 가능.
--     (단종 제품의 가격/태그/재활성 변경은 회계/감사 추적이 강한 admin 결정 사항)

CREATE TABLE product_edit_requests (
    id              UUID         PRIMARY KEY,
    entity_id       UUID         NOT NULL,    -- Product.id (FK 미강제)
    requester_id    UUID         NOT NULL,
    requester_name  VARCHAR(50)  NOT NULL,
    request_type    VARCHAR(20)  NOT NULL,    -- EDIT | DELETE
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | EXPIRED
    target_role     VARCHAR(20)  NOT NULL,    -- WAREHOUSE | MANAGER (제품은 MANAGER 기본)
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

COMMENT ON TABLE product_edit_requests IS
    'PR-H4b 제품 마스터 수정/삭제 요청 워크플로우 — Phase 12 Step 4b BE-C. DISCONTINUED 제품의 mutation 잠금 해제 채널';

COMMENT ON COLUMN product_edit_requests.request_type IS
    'EDIT (가격/태그/재활성 등 필드 수정 요청) | DELETE (제품 자체 soft-delete 요청)';

COMMENT ON COLUMN product_edit_requests.status IS
    'PENDING (요청 직후) → APPROVED (수락, 1회 mutation 가능) | REJECTED (거절) | EXPIRED (자동 만료)';

COMMENT ON COLUMN product_edit_requests.target_role IS
    'WAREHOUSE | MANAGER. 제품 마스터 도메인 default = MANAGER (admin 결정 권한)';

CREATE INDEX ix_product_edit_requests_entity_status
    ON product_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_product_edit_requests_target_role
    ON product_edit_requests (target_role, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_product_edit_requests_pending_expires
    ON product_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING';
