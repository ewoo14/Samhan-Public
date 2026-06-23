-- V40__add_notes_receivable.sql
-- G-1 받을어음 도메인 신규 테이블.
--
-- 적용 원칙:
--   * BaseEntity 7 audit + Soft Delete.
--   * enum 영속 값은 CHECK 제약을 동반한다 ([[enum-expansion-check-constraint]]).
--   * partner_id UUID 는 내부 join 키이며 사용자 화면에는 노출하지 않는다.

CREATE TABLE IF NOT EXISTS notes_receivable (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    partner_id     UUID          NOT NULL,
    note_no        VARCHAR(50)   NOT NULL,
    issue_date     DATE          NOT NULL,
    maturity_date  DATE          NOT NULL,
    amount         NUMERIC(18,2) NOT NULL,
    note_type      VARCHAR(30)   NOT NULL
                   CHECK (note_type IN ('PROMISSORY', 'BILL_OF_EXCHANGE')),
    status         VARCHAR(30)   NOT NULL
                   CHECK (status IN ('BOARDING', 'COLLECTING', 'SETTLED', 'DISHONORED')),
    memo           TEXT,

    -- BaseEntity 7 audit
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(50)   NOT NULL DEFAULT 'SYSTEM',
    modified_at    TIMESTAMP,
    modified_by    VARCHAR(50),
    deleted_at     TIMESTAMP,
    deleted_by     VARCHAR(50),
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_notes_receivable PRIMARY KEY (id),
    CONSTRAINT ck_notes_receivable_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_notes_receivable_maturity_after_issue CHECK (maturity_date >= issue_date)
);

COMMENT ON TABLE notes_receivable IS
    '받을어음 — 회계 보고 스위트 G-1 신규 쓰기 도메인';
COMMENT ON COLUMN notes_receivable.partner_id IS
    '거래처 내부 UUID. API/화면에는 노출하지 않고 partnerCode/bizNo/name 으로 변환';
COMMENT ON COLUMN notes_receivable.note_no IS '어음번호. 활성 row 기준 중복 불가';
COMMENT ON COLUMN notes_receivable.note_type IS
    '어음 종류: PROMISSORY=약속어음, BILL_OF_EXCHANGE=환어음';
COMMENT ON COLUMN notes_receivable.status IS
    '상태: BOARDING=보유, COLLECTING=추심, SETTLED=결제완료, DISHONORED=부도';

CREATE UNIQUE INDEX IF NOT EXISTS uq_notes_receivable_note_no_active
    ON notes_receivable (note_no)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_notes_receivable_maturity_date
    ON notes_receivable (maturity_date)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_notes_receivable_partner_id
    ON notes_receivable (partner_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_notes_receivable_status
    ON notes_receivable (status)
    WHERE is_deleted = FALSE;
