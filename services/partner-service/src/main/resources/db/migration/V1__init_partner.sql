-- V1__init_partner.sql
-- Phase 9 W1 partner-service — initial schema (2 entity).
-- BaseEntity audit columns mirror inventory-service.V1 정확히.
-- Soft-delete 는 application-side @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 VARCHAR(N) (CHAR/bpchar 금지)
--   * 가격/금액은 NUMERIC(15,2)
--   * 수량은 INT
--   * detail/payload 등 가변 큰 텍스트는 TEXT

----------------------------------------------------------------------
-- 1) partners — 거래처 마스터
--    partner_code = 사용자 노출 식별자 (UUID 비공개 가드).
--    biz_no       = 사업자번호 (한국 표준 10자리, '-' 포함 13자리 입력 가능).
--    credit_limit / outstanding_balance = 신용 거래 한도 / 미수금 잔액.
----------------------------------------------------------------------
CREATE TABLE partners (
    id                      UUID         PRIMARY KEY,
    partner_code            VARCHAR(50)  NOT NULL,
    biz_no                  VARCHAR(20)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    address                 VARCHAR(500),
    phone                   VARCHAR(30),
    credit_limit            NUMERIC(15,2) NOT NULL DEFAULT 0,
    outstanding_balance     NUMERIC(15,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20)  NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(50)  NOT NULL,
    modified_at             TIMESTAMP,
    modified_by             VARCHAR(50),
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(50),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE
);

-- partner_code 활성 행 unique (soft-delete 후 재사용 허용)
CREATE UNIQUE INDEX ux_partners_partner_code_active
    ON partners (partner_code)
    WHERE is_deleted = FALSE;

-- biz_no 활성 행 unique
CREATE UNIQUE INDEX ux_partners_biz_no_active
    ON partners (biz_no)
    WHERE is_deleted = FALSE;

-- 거래처명 검색 / 상태 필터
CREATE INDEX ix_partners_name
    ON partners (name);

CREATE INDEX ix_partners_status_active
    ON partners (status, is_deleted);

----------------------------------------------------------------------
-- 2) partner_credit_history — 신용 거래 이력
--    SLIP_ISSUED         : 슬립 발행 시 미수금 증가 (amount 양수)
--    PAYMENT             : 결제 입금 시 미수금 차감 (amount 음수)
--    CREDIT_LIMIT_CHANGE : 한도 변경 (delta_credit_limit 양/음수)
----------------------------------------------------------------------
CREATE TABLE partner_credit_history (
    id                          UUID         PRIMARY KEY,
    partner_id                  UUID         NOT NULL REFERENCES partners(id),
    event_type                  VARCHAR(30)  NOT NULL,
    amount                      NUMERIC(15,2) NOT NULL DEFAULT 0,
    delta_credit_limit          NUMERIC(15,2) NOT NULL DEFAULT 0,
    balance_after               NUMERIC(15,2) NOT NULL,
    credit_limit_after          NUMERIC(15,2) NOT NULL,
    reference_no                VARCHAR(50),
    note                        VARCHAR(500),
    occurred_at                 TIMESTAMP    NOT NULL,

    created_at                  TIMESTAMP    NOT NULL,
    created_by                  VARCHAR(50)  NOT NULL,
    modified_at                 TIMESTAMP,
    modified_by                 VARCHAR(50),
    deleted_at                  TIMESTAMP,
    deleted_by                  VARCHAR(50),
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_partner_credit_history_partner_occurred
    ON partner_credit_history (partner_id, occurred_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_credit_history_event_occurred
    ON partner_credit_history (event_type, occurred_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_credit_history_reference_no
    ON partner_credit_history (reference_no)
    WHERE is_deleted = FALSE AND reference_no IS NOT NULL;
