-- V3__add_inventory_audit.sql
-- 재고 실사 (Inventory Audit) — Phase 10 P2-6 슬라이스 9.
--
-- 한국 일반기업회계기준 — 매년 12월 31일 의무 재고 실사 (memory project_korean_accounting).
-- 차이 발생 시 자동 분개 trigger (150 재고자산 / 919 재고감모손실).
--
-- 컬럼 타입 컨벤션 (V1 mirror):
--   * 짧은 문자열 VARCHAR(N), CHAR/bpchar 금지
--   * 가격/금액 NUMERIC(15,2)
--   * 수량 INT (소수점 미사용)
--
-- soft-delete 는 application-side 의 @SQLRestriction("is_deleted = false") 로 강제.

----------------------------------------------------------------------
-- 1) inventory_audits — 재고 실사 마스터 (창고+일자 단위)
----------------------------------------------------------------------
CREATE TABLE inventory_audits (
    id                  UUID            PRIMARY KEY,
    audit_no            VARCHAR(30)     NOT NULL,
    warehouse_id        UUID            NOT NULL REFERENCES warehouses(id),
    audit_date          DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    total_diff_amount   NUMERIC(15,2)   NOT NULL DEFAULT 0,

    -- BaseEntity audit columns
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_inventory_audits_no_active
    ON inventory_audits (audit_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_inventory_audits_warehouse_date_active
    ON inventory_audits (warehouse_id, audit_date, is_deleted);

CREATE INDEX ix_inventory_audits_status_active
    ON inventory_audits (status, is_deleted);

----------------------------------------------------------------------
-- 2) inventory_audit_lines — 재고 실사 라인 (제품별 시스템 vs 실물)
--    expected_qty: 실사 시점 system stock snapshot
--    actual_qty: 실사자 입력 (NULL = 미입력)
--    diff_qty: actual - expected (애플리케이션 계산 영속)
--    unit_cost: snapshot at audit start (lot 평균/표준원가)
--    diff_amount: diff_qty * unit_cost
--    barcode_scanned: 바코드 스캔 여부 (false = 수동 입력)
----------------------------------------------------------------------
CREATE TABLE inventory_audit_lines (
    id                  UUID            PRIMARY KEY,
    audit_id            UUID            NOT NULL REFERENCES inventory_audits(id),
    product_id          UUID            NOT NULL,
    product_name        VARCHAR(200)    NOT NULL,
    expected_qty        INT             NOT NULL,
    actual_qty          INT,
    diff_qty            INT             NOT NULL DEFAULT 0,
    unit_cost           NUMERIC(15,2)   NOT NULL DEFAULT 0,
    diff_amount         NUMERIC(15,2)   NOT NULL DEFAULT 0,
    barcode_scanned     BOOLEAN         NOT NULL DEFAULT FALSE,
    scanned_at          TIMESTAMP,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_inventory_audit_lines_audit_active
    ON inventory_audit_lines (audit_id, is_deleted);

CREATE INDEX ix_inventory_audit_lines_product_active
    ON inventory_audit_lines (product_id, is_deleted);

-- (audit_id, product_id) 한 실사 내 제품 중복 라인 금지 (snapshot 시점에 1건씩)
CREATE UNIQUE INDEX ux_inventory_audit_lines_audit_product_active
    ON inventory_audit_lines (audit_id, product_id)
    WHERE is_deleted = FALSE;
