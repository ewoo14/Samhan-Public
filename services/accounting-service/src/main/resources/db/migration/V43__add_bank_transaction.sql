-- V43__add_bank_transaction.sql
-- H-1 BankTransaction 도메인 + CSV/KFTC 소스 무관 통장 거래 테이블.
--
-- 적용 원칙:
--   * BaseEntity 7 audit + Soft Delete.
--   * enum 영속 값은 CHECK 제약을 동반한다 ([[enum-expansion-check-constraint]]).
--   * id / matched_partner_id / matched_journal_id UUID 는 내부 join 키이며 사용자 화면에는 노출하지 않는다.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS bank_transaction (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    transacted_at         TIMESTAMP     NOT NULL,
    txn_type              VARCHAR(20)   NOT NULL
                          CHECK (txn_type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount                NUMERIC(18,2) NOT NULL,
    balance_after         NUMERIC(18,2),
    description           VARCHAR(500)  NOT NULL,
    counterparty_name     VARCHAR(120),
    counterparty_account  VARCHAR(80),
    bank_account_label    VARCHAR(120)  NOT NULL,
    source                VARCHAR(20)   NOT NULL
                          CHECK (source IN ('CSV_IMPORT', 'KFTC')),
    external_ref          VARCHAR(128)  NOT NULL,
    match_status          VARCHAR(20)   NOT NULL
                          CHECK (match_status IN ('UNREFLECTED', 'REFLECTED', 'FORCED')),
    matched_partner_id    UUID,
    matched_journal_id    UUID,

    -- BaseEntity 7 audit
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(50)   NOT NULL DEFAULT 'SYSTEM',
    modified_at           TIMESTAMP,
    modified_by           VARCHAR(50),
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(50),
    is_deleted            BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_bank_transaction PRIMARY KEY (id),
    CONSTRAINT ck_bank_transaction_amount_positive CHECK (amount > 0)
);

COMMENT ON TABLE bank_transaction IS
    '통장 입출금 거래 — 회계 보고 스위트 H-1 신규 쓰기 도메인';
COMMENT ON COLUMN bank_transaction.bank_account_label IS
    '우리 측 은행계좌 표시명. UUID 대신 화면/API 필터 식별자로 사용';
COMMENT ON COLUMN bank_transaction.external_ref IS
    '외부 참조키. CSV import 는 매핑값 또는 행 내용 기반 SHA-256 생성값';
COMMENT ON COLUMN bank_transaction.match_status IS
    '매칭상태: UNREFLECTED=미반영, REFLECTED=회계반영, FORCED=강제';

CREATE INDEX IF NOT EXISTS idx_bank_transaction_transacted_at
    ON bank_transaction (transacted_at)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bank_transaction_match_status
    ON bank_transaction (match_status)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bank_transaction_bank_account
    ON bank_transaction (bank_account_label)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_transaction_external_active
    ON bank_transaction (bank_account_label, transacted_at, amount, external_ref)
    WHERE is_deleted = FALSE;
