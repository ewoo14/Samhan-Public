-- V41__add_collection_plan.sql
-- G-2 수금계획 도메인 신규 테이블.
--
-- 적용 원칙:
--   * BaseEntity 7 audit + Soft Delete.
--   * enum 영속 값은 CHECK 제약을 동반한다 ([[enum-expansion-check-constraint]]).
--   * id/partner_id UUID 는 내부 join 키이며 사용자 화면에는 노출하지 않는다.

CREATE TABLE IF NOT EXISTS collection_plan (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    plan_no         VARCHAR(40)   NOT NULL,
    partner_id      UUID          NOT NULL,
    planned_date    DATE          NOT NULL,
    planned_amount  NUMERIC(18,2) NOT NULL,
    basis           VARCHAR(30)   NOT NULL
                    CHECK (basis IN ('RECEIVABLE_BALANCE', 'NOTE_MATURITY', 'MANUAL')),
    status          VARCHAR(30)   NOT NULL
                    CHECK (status IN ('PLANNED', 'COLLECTED', 'OVERDUE')),
    memo            TEXT,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)   NOT NULL DEFAULT 'SYSTEM',
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_collection_plan PRIMARY KEY (id),
    CONSTRAINT ck_collection_plan_amount_positive CHECK (planned_amount > 0)
);

COMMENT ON TABLE collection_plan IS
    '수금계획 — 회계 보고 스위트 G-2 신규 쓰기 도메인';
COMMENT ON COLUMN collection_plan.plan_no IS
    '수금계획 업무 식별자. UUID 대신 API/화면/상태전이 path 에 사용';
COMMENT ON COLUMN collection_plan.partner_id IS
    '거래처 내부 UUID. API/화면에는 노출하지 않고 partnerCode/bizNo/name 으로 변환';
COMMENT ON COLUMN collection_plan.basis IS
    '수금계획 근거: RECEIVABLE_BALANCE=외상매출잔액, NOTE_MATURITY=어음만기, MANUAL=수동';
COMMENT ON COLUMN collection_plan.status IS
    '상태: PLANNED=예정, COLLECTED=수금완료, OVERDUE=연체';

CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_plan_plan_no_active
    ON collection_plan (plan_no)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_collection_plan_planned_date
    ON collection_plan (planned_date)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_collection_plan_partner_id
    ON collection_plan (partner_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_collection_plan_status
    ON collection_plan (status)
    WHERE is_deleted = FALSE;
