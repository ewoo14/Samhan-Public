-- V1__init_inventory_service.sql
-- Inventory Service — initial schema for warehouse + stock domain (plan §3.1).
-- BaseEntity audit columns mirror product-service.V1__init_product_service.sql 정확히.
-- Soft-delete 는 application-side 의 @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 모두 VARCHAR(N), CHAR/bpchar 금지
--   * 가격/금액은 NUMERIC(15,2)
--   * Money 외 수량은 INT (재고는 단위 단위 — 소수점 미사용 슬라이스)

----------------------------------------------------------------------
-- 1) warehouses — 창고 마스터 (자체/임대/가상)
----------------------------------------------------------------------
CREATE TABLE warehouses (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    address         VARCHAR(255),
    display_order   INT          NOT NULL DEFAULT 0,
    description     VARCHAR(500),

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_warehouses_code_active
    ON warehouses (code)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_warehouses_type_active
    ON warehouses (type, is_deleted);

----------------------------------------------------------------------
-- 2) stock_lots — 입고 단위 로트 (FIFO 키: received_at)
--    productId 는 logical reference (FK 없음 — 다른 서비스 소유)
----------------------------------------------------------------------
CREATE TABLE stock_lots (
    id                  UUID            PRIMARY KEY,
    product_id          UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL REFERENCES warehouses(id),
    lot_no              VARCHAR(50),
    quantity            INT             NOT NULL CHECK (quantity >= 0),
    initial_quantity    INT             NOT NULL CHECK (initial_quantity > 0),
    received_at         TIMESTAMP       NOT NULL,
    unit_cost           NUMERIC(15,2),
    status              VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    source_transfer_id  UUID,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_stock_lots_fifo
    ON stock_lots (product_id, warehouse_id, received_at)
    WHERE is_deleted = FALSE AND status = 'AVAILABLE';

CREATE INDEX ix_stock_lots_warehouse_active
    ON stock_lots (warehouse_id, is_deleted);

CREATE INDEX ix_stock_lots_product_active
    ON stock_lots (product_id, is_deleted);

----------------------------------------------------------------------
-- 3) stock_balances — (product, warehouse) 집계 + 낙관적 락 (version)
----------------------------------------------------------------------
CREATE TABLE stock_balances (
    id              UUID         PRIMARY KEY,
    product_id      UUID         NOT NULL,
    warehouse_id    UUID         NOT NULL REFERENCES warehouses(id),
    available_qty   INT          NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty    INT          NOT NULL DEFAULT 0 CHECK (reserved_qty  >= 0),
    total_qty       INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_stock_balances_pw_active
    ON stock_balances (product_id, warehouse_id)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_stock_balances_product_active
    ON stock_balances (product_id, is_deleted);

----------------------------------------------------------------------
-- 4) stock_movements — append-only 감사 (soft-delete 사용 안 함)
----------------------------------------------------------------------
CREATE TABLE stock_movements (
    id              UUID         PRIMARY KEY,
    lot_id          UUID         NOT NULL,
    product_id      UUID         NOT NULL,
    warehouse_id    UUID         NOT NULL,
    movement_type   VARCHAR(20)  NOT NULL,
    quantity_delta  INT          NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    UUID,
    note            VARCHAR(500),
    occurred_at     TIMESTAMP    NOT NULL,
    actor_user_id   VARCHAR(50)  NOT NULL,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_stock_movements_lot_occurred
    ON stock_movements (lot_id, occurred_at DESC);

CREATE INDEX ix_stock_movements_product_occurred
    ON stock_movements (product_id, occurred_at DESC);

CREATE INDEX ix_stock_movements_warehouse_occurred
    ON stock_movements (warehouse_id, occurred_at DESC);

CREATE INDEX ix_stock_movements_reference
    ON stock_movements (reference_type, reference_id);

----------------------------------------------------------------------
-- 5) stock_transfers — 이동전표 헤더
----------------------------------------------------------------------
CREATE TABLE stock_transfers (
    id                          UUID         PRIMARY KEY,
    transfer_no                 VARCHAR(30)  NOT NULL,
    source_warehouse_id         UUID         NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id    UUID         NOT NULL REFERENCES warehouses(id),
    reason                      VARCHAR(20)  NOT NULL,
    reason_detail               VARCHAR(500),
    status                      VARCHAR(20)  NOT NULL,
    requester_id                VARCHAR(50)  NOT NULL,
    approver_id                 VARCHAR(50),
    requested_at                TIMESTAMP    NOT NULL,
    approved_at                 TIMESTAMP,
    shipped_at                  TIMESTAMP,
    received_at                 TIMESTAMP,
    confirmed_at                TIMESTAMP,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_transfer_diff_warehouse
        CHECK (source_warehouse_id <> destination_warehouse_id)
);

CREATE UNIQUE INDEX ux_stock_transfers_no_active
    ON stock_transfers (transfer_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_stock_transfers_status_active
    ON stock_transfers (status, is_deleted);

CREATE INDEX ix_stock_transfers_source_active
    ON stock_transfers (source_warehouse_id, is_deleted);

CREATE INDEX ix_stock_transfers_destination_active
    ON stock_transfers (destination_warehouse_id, is_deleted);

----------------------------------------------------------------------
-- 6) stock_transfer_lines — 이동전표 라인
----------------------------------------------------------------------
CREATE TABLE stock_transfer_lines (
    id                  UUID         PRIMARY KEY,
    transfer_id         UUID         NOT NULL REFERENCES stock_transfers(id),
    product_id          UUID         NOT NULL,
    requested_quantity  INT          NOT NULL CHECK (requested_quantity > 0),
    shipped_quantity    INT          NOT NULL DEFAULT 0 CHECK (shipped_quantity >= 0),
    received_quantity   INT          NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    source_lot_id       UUID,
    destination_lot_id  UUID,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_stock_transfer_lines_transfer_active
    ON stock_transfer_lines (transfer_id, is_deleted);
