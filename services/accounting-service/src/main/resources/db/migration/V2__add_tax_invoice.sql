-- V2__add_tax_invoice.sql
-- Phase 10 Step 8 — P0-4 #3 세금계산서 발행 + 자동 분개 (110/255/400 패턴).
--
-- TaxInvoice 헤더 + TaxInvoiceLine 라인. 한국 일반기업회계기준 표준 양식.
-- VAT 자동 계산 = supply_amount * 0.10 (라인/헤더 모두). DRAFT → ISSUED → CANCELLED.
--
-- 컬럼 타입 컨벤션 (V1 답습):
--   * 짧은 문자열 VARCHAR(N)
--   * 금액 NUMERIC(15,2)
--   * 낙관적 락 version BIGINT
--   * BaseEntity 7 audit columns

----------------------------------------------------------------------
-- 1) tax_invoices — 세금계산서 헤더
--    tax_invoice_no: YYYYMMDD-NNNN, ISSUED 시점에 채번 (DRAFT 면 NULL).
--    공급받는자 스냅샷 (partner_business_no/name/address) — 발행 시점 보존.
----------------------------------------------------------------------
CREATE TABLE tax_invoices (
    id                       UUID            PRIMARY KEY,
    tax_invoice_no           VARCHAR(20),
    partner_id               UUID            NOT NULL,
    partner_business_no      VARCHAR(20),
    partner_name             VARCHAR(200)    NOT NULL,
    partner_address          VARCHAR(500),
    supply_date              DATE            NOT NULL,
    supply_amount            NUMERIC(15,2)   NOT NULL DEFAULT 0,
    vat_amount               NUMERIC(15,2)   NOT NULL DEFAULT 0,
    total_amount             NUMERIC(15,2)   NOT NULL DEFAULT 0,
    status                   VARCHAR(20)     NOT NULL,
    issued_at                TIMESTAMP,
    issued_by                VARCHAR(50),
    cancelled_at             TIMESTAMP,
    cancelled_by             VARCHAR(50),
    journal_id               UUID,
    reverse_journal_id       UUID,
    e_tax_external_id        VARCHAR(100),
    description              VARCHAR(500),
    version                  BIGINT          NOT NULL DEFAULT 0,

    -- BaseEntity 7 audit
    created_at               TIMESTAMP       NOT NULL,
    created_by               VARCHAR(50)     NOT NULL,
    modified_at              TIMESTAMP,
    modified_by              VARCHAR(50),
    deleted_at               TIMESTAMP,
    deleted_by               VARCHAR(50),
    is_deleted               BOOLEAN         NOT NULL DEFAULT FALSE
);

-- ISSUED/CANCELLED 분개의 tax_invoice_no UNIQUE 보장 (DRAFT 의 NULL 허용).
CREATE UNIQUE INDEX ux_tax_invoices_no_active
    ON tax_invoices (tax_invoice_no)
    WHERE is_deleted = FALSE AND tax_invoice_no IS NOT NULL;

CREATE INDEX ix_tax_invoices_status_active
    ON tax_invoices (status, is_deleted);

CREATE INDEX ix_tax_invoices_partner_active
    ON tax_invoices (partner_id, is_deleted);

CREATE INDEX ix_tax_invoices_supply_date_active
    ON tax_invoices (supply_date, is_deleted);

----------------------------------------------------------------------
-- 2) tax_invoice_lines — 세금계산서 라인
--    line_no 표시 순번 (1-based). vat_amount = supply_amount * 0.10 (서비스 계산).
----------------------------------------------------------------------
CREATE TABLE tax_invoice_lines (
    id                       UUID            PRIMARY KEY,
    tax_invoice_id           UUID            NOT NULL REFERENCES tax_invoices(id),
    line_no                  INT             NOT NULL,
    item_name                VARCHAR(200)    NOT NULL,
    spec                     VARCHAR(100),
    quantity                 NUMERIC(15,2)   NOT NULL DEFAULT 0,
    unit_price               NUMERIC(15,2)   NOT NULL DEFAULT 0,
    supply_amount            NUMERIC(15,2)   NOT NULL DEFAULT 0,
    vat_amount               NUMERIC(15,2)   NOT NULL DEFAULT 0,
    memo                     VARCHAR(500),

    created_at               TIMESTAMP       NOT NULL,
    created_by               VARCHAR(50)     NOT NULL,
    modified_at              TIMESTAMP,
    modified_by              VARCHAR(50),
    deleted_at               TIMESTAMP,
    deleted_by               VARCHAR(50),
    is_deleted               BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_tax_invoice_lines_invoice_active
    ON tax_invoice_lines (tax_invoice_id, is_deleted);

----------------------------------------------------------------------
-- 3) tax_invoice_number_sequences — YYYYMMDD-NNNN 채번
--    JournalNumberSequence 답습. issue_date UNIQUE.
----------------------------------------------------------------------
CREATE TABLE tax_invoice_number_sequences (
    id              UUID         PRIMARY KEY,
    issue_date      DATE         NOT NULL,
    last_seq        INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ux_tax_invoice_number_sequences_date UNIQUE (issue_date)
);

----------------------------------------------------------------------
-- 4) 부가세예수금 계정 코드 추가 — V1 시드는 220 만 보유.
--    매뉴얼 (docs/manual/03-회계/03-세금계산서.md §2-4) + 본 슬라이스 작업범위는
--    255 (부가세예수금) 사용. 한국 일반기업회계기준 코드체계는 220/255 모두 활용 가능 →
--    255 신규 시드 (대분류 200 부채). 기존 220 은 호환을 위해 유지.
----------------------------------------------------------------------
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('255',  '부가세예수금', 'LIABILITY', '200', TRUE,  2550, CURRENT_TIMESTAMP, 'SYSTEM');
