-- V13__add_estimate.sql
-- Slip Service — P2-1 (Stage 4): 영업 견적서 도메인.
--
-- 매뉴얼 출처: docs/manual/01-영업/06-견적서.md §2 (P2-1 catalog)
--
-- 컨텍스트:
--   * estimate (견적서) 헤더 + estimate_lines (라인) 신규 2 테이블.
--   * Slip 의 partial UNIQUE INDEX (V1) 패턴 그대로 적용 — estimate_no 도 active 한 row 만 유일.
--   * 만료/상태 전이는 도메인 레이어 (Estimate.send / accept / reject / convert) 가 가드.
--   * 견적 → 슬립 자동 변환은 EstimateToSlipConverter service 가 처리 (CONVERTED 전이 시점에
--     estimate_lines → slip_lines copy + converted_slip_id FK 기록).
--
-- 컬럼 컨벤션 (V1 계승):
--   * 가격/금액 NUMERIC(15,2), 라인 합계는 NUMERIC(17,2)
--   * 짧은 문자열은 VARCHAR(N), CHAR/bpchar 금지
--   * 낙관적 락: version BIGINT NOT NULL DEFAULT 0 (Slip 패턴 일관)
--
-- 회귀 영향:
--   * estimates / estimate_lines 신규 — 기존 IT 의 slip 테이블에 영향 0.
--   * converted_slip_id FK 는 logical (REFERENCES 미선언) — slip-service 내부지만
--     slips 테이블과 1:1 의존 결합 회피 (Soft Delete 시 slips row 가 살아있음을 가정).

----------------------------------------------------------------------
-- 1) estimates — 견적서 헤더
----------------------------------------------------------------------
CREATE TABLE estimates (
    id                          UUID         PRIMARY KEY,
    estimate_no                 VARCHAR(30)  NOT NULL,
    estimate_date               DATE         NOT NULL,
    seq_no                      INT          NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    partner_id                  UUID,
    partner_name                VARCHAR(100),
    partner_business_no         VARCHAR(20),
    partner_address             VARCHAR(200),
    valid_until                 DATE,
    total_supply                NUMERIC(17,2) NOT NULL DEFAULT 0,
    total_vat                   NUMERIC(17,2) NOT NULL DEFAULT 0,
    total_amount                NUMERIC(17,2) NOT NULL DEFAULT 0,
    converted_slip_id           UUID,
    sent_at                     TIMESTAMP,
    accepted_at                 TIMESTAMP,
    rejected_at                 TIMESTAMP,
    converted_at                TIMESTAMP,
    memo                        VARCHAR(1000),
    requester_id                VARCHAR(50)  NOT NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,

    -- BaseEntity audit (plan §8) — Slip 과 동일 7 필드
    created_at                  TIMESTAMP    NOT NULL,
    created_by                  VARCHAR(50)  NOT NULL,
    modified_at                 TIMESTAMP,
    modified_by                 VARCHAR(50),
    deleted_at                  TIMESTAMP,
    deleted_by                  VARCHAR(50),
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE estimates IS
    'P2-1 영업 견적서 헤더 — QUOTE_DRAFT/QUOTE_SENT/QUOTE_ACCEPTED/QUOTE_REJECTED/QUOTE_CONVERTED 5 단계 라이프사이클';

COMMENT ON COLUMN estimates.estimate_no IS
    '견적번호 EQ-YYYYMMDD-NNNN — 자동 채번 (날짜별 시퀀스, slip 의 SlipNumberSequence 와 별도)';

COMMENT ON COLUMN estimates.converted_slip_id IS
    'CONVERTED 전이 시점 자동 발행된 Slip(OUTBOUND DRAFT) 의 id — logical FK';

CREATE UNIQUE INDEX ux_estimates_estimate_no_active
    ON estimates (estimate_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_estimates_status_active
    ON estimates (status, is_deleted);

CREATE INDEX ix_estimates_partner_active
    ON estimates (partner_id, is_deleted);

CREATE INDEX ix_estimates_estimate_date_active
    ON estimates (estimate_date, is_deleted);

----------------------------------------------------------------------
-- 2) estimate_lines — 견적 라인 (cascade ALL, orphanRemoval)
--    productId 는 product-service logical reference (FK 없음 — slip_lines 패턴 일관)
----------------------------------------------------------------------
CREATE TABLE estimate_lines (
    id              UUID            PRIMARY KEY,
    estimate_id     UUID            NOT NULL REFERENCES estimates(id),
    line_no         INT             NOT NULL,
    product_id      UUID            NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    model_name      VARCHAR(100),
    specification   VARCHAR(50),
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(15,2)   NOT NULL CHECK (unit_price >= 0),
    supply_amount   NUMERIC(17,2)   NOT NULL CHECK (supply_amount >= 0),
    vat_amount      NUMERIC(15,2)   NOT NULL CHECK (vat_amount >= 0),
    line_total      NUMERIC(17,2)   NOT NULL CHECK (line_total >= 0),
    note            VARCHAR(200),

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_estimate_lines_estimate_active
    ON estimate_lines (estimate_id, is_deleted);

CREATE INDEX ix_estimate_lines_product_active
    ON estimate_lines (product_id, is_deleted);

----------------------------------------------------------------------
-- 3) estimate_number_sequences — 일자별 채번 시퀀스 (slip_number_sequences 와 별도)
----------------------------------------------------------------------
CREATE TABLE estimate_number_sequences (
    id              UUID         PRIMARY KEY,
    estimate_date   DATE         NOT NULL,
    last_seq        INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ux_estimate_number_sequences_date UNIQUE (estimate_date)
);
