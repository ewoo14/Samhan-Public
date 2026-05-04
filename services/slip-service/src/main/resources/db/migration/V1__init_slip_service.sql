-- V1__init_slip_service.sql
-- Slip Service — initial schema for Slip(STI) + SlipLine + SlipNumberSequence (plan §3.1).
-- BaseEntity audit columns mirror inventory-service.V1__init_inventory_service.sql 정확히.
-- Soft-delete 는 application-side 의 @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 모두 VARCHAR(N), CHAR/bpchar 금지
--   * 가격/금액은 NUMERIC(15,2), 라인 합계는 NUMERIC(17,2) (수량 곱셈 마진)
--   * 수량은 INT (재고는 단위 단위 — 소수점 미사용 슬라이스)
--   * 낙관적 락: version BIGINT NOT NULL DEFAULT 0

----------------------------------------------------------------------
-- 1) slips — 전표 헤더 (Single Table Inheritance, slip_type discriminator)
--    출고/입고 1 테이블 + nullable 필드 (Q1 결정)
----------------------------------------------------------------------
CREATE TABLE slips (
    id                          UUID         PRIMARY KEY,
    slip_type                   VARCHAR(20)  NOT NULL,
    slip_no                     VARCHAR(30)  NOT NULL,
    slip_date                   DATE         NOT NULL,
    seq_no                      INT          NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    partner_id                  UUID,
    partner_name                VARCHAR(100),
    source_warehouse_id         UUID,
    destination_warehouse_id    UUID,
    delivery_tag                VARCHAR(30),
    memo                        VARCHAR(1000),
    requester_id                VARCHAR(50)  NOT NULL,
    accepted_by                 VARCHAR(50),
    accepted_at                 TIMESTAMP,
    completed_at                TIMESTAMP,
    confirmed_at                TIMESTAMP,
    version                     BIGINT       NOT NULL DEFAULT 0,

    -- BaseEntity audit (plan §8)
    created_at                  TIMESTAMP    NOT NULL,
    created_by                  VARCHAR(50)  NOT NULL,
    modified_at                 TIMESTAMP,
    modified_by                 VARCHAR(50),
    deleted_at                  TIMESTAMP,
    deleted_by                  VARCHAR(50),
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_slips_slip_no_active
    ON slips (slip_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_slips_status_active
    ON slips (status, is_deleted);

CREATE INDEX ix_slips_type_status_active
    ON slips (slip_type, status, is_deleted);

CREATE INDEX ix_slips_slip_date_active
    ON slips (slip_date, is_deleted);

CREATE INDEX ix_slips_partner_active
    ON slips (partner_id, is_deleted);

CREATE INDEX ix_slips_requester_active
    ON slips (requester_id, is_deleted);

----------------------------------------------------------------------
-- 2) slip_lines — 전표 라인 (cascade ALL, orphanRemoval)
--    productId 는 product-service logical reference (FK 없음)
----------------------------------------------------------------------
CREATE TABLE slip_lines (
    id              UUID            PRIMARY KEY,
    slip_id         UUID            NOT NULL REFERENCES slips(id),
    product_id      UUID            NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    model_name      VARCHAR(100),
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(15,2)   NOT NULL CHECK (unit_price >= 0),
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

CREATE INDEX ix_slip_lines_slip_active
    ON slip_lines (slip_id, is_deleted);

CREATE INDEX ix_slip_lines_product_active
    ON slip_lines (product_id, is_deleted);

----------------------------------------------------------------------
-- 3) slip_number_sequences — 일자별 채번 시퀀스 (atomic 보조)
----------------------------------------------------------------------
CREATE TABLE slip_number_sequences (
    id              UUID         PRIMARY KEY,
    slip_date       DATE         NOT NULL,
    last_seq        INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ux_slip_number_sequences_date UNIQUE (slip_date)
);
