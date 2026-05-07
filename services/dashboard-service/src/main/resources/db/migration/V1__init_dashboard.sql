-- V1__init_dashboard.sql
-- Phase 9 W4 dashboard-service — initial schema (3 entity + 2 materialized view).
-- BaseEntity audit columns mirror partner-service / groupware-service / notification-service V1 정확히.
-- Soft-delete 는 application-side @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 VARCHAR(N) (CHAR/bpchar 금지)
--   * 금액 / 수량 / 비율은 NUMERIC(20, 4) (분수 재고 + 통화 4자리 호환)
--   * 시간은 TIMESTAMP (Hibernate JPA LocalDateTime 매핑)
--   * 날짜는 DATE (Hibernate JPA LocalDate 매핑)

----------------------------------------------------------------------
-- 1) kpi_snapshots — KPI 일/주/월 스냅샷.
--    category         = DAILY_SALES / WEEKLY_SALES / MONTHLY_SALES / ORDER_COUNT
--                       / ACTIVE_PARTNERS / STOCK_TURNOVER
--    snapshot_date    = 스냅샷 기준 일자
--    value            = 산출 값 (NUMERIC(20,4) — 금액 / 카운트 / 비율 모두 단일 컬럼)
----------------------------------------------------------------------
CREATE TABLE kpi_snapshots (
    id              UUID            PRIMARY KEY,
    snapshot_date   DATE            NOT NULL,
    category        VARCHAR(30)     NOT NULL,
    value           NUMERIC(20, 4)  NOT NULL,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 활성 행 한정 unique — 같은 날짜 / 카테고리 중복 방지 (재집계 시 update)
CREATE UNIQUE INDEX ux_kpi_snapshots_date_category_active
    ON kpi_snapshots (snapshot_date, category)
    WHERE is_deleted = FALSE;

-- 카테고리별 시계열 lookup (admin dashboard 그래프)
CREATE INDEX ix_kpi_snapshots_category_date_active
    ON kpi_snapshots (category, snapshot_date DESC)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) realtime_stocks — 실시간 재고 캐시 (inventory-service 동기 row).
--    product_id       = inventory-service product UUID
--    warehouse_code   = 사용자 노출 식별자 (UUID 비공개 가드)
--    quantity         = NUMERIC(20,4) — 분수 재고 (kg 단위 자재) 호환
--    refreshed_at     = 데이터 신선도 (BaseEntity.modifiedAt 과 별개 의미)
----------------------------------------------------------------------
CREATE TABLE realtime_stocks (
    id              UUID            PRIMARY KEY,
    product_id      UUID            NOT NULL,
    warehouse_code  VARCHAR(20)     NOT NULL,
    quantity        NUMERIC(20, 4)  NOT NULL,
    refreshed_at    TIMESTAMP       NOT NULL,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 활성 행 한정 unique — 같은 product + 창고 1행만 (refresh 시 update)
CREATE UNIQUE INDEX ux_realtime_stocks_product_warehouse_active
    ON realtime_stocks (product_id, warehouse_code)
    WHERE is_deleted = FALSE;

-- 창고별 lookup
CREATE INDEX ix_realtime_stocks_warehouse_active
    ON realtime_stocks (warehouse_code)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 3) sales_aggregates — 일별 / 거래처별 매출 집계.
--    aggregate_date   = 집계 기준 일자
--    partner_id       = 거래처 UUID (UUID 비공개 가드 — partner-service lookup 으로 partnerCode 매핑)
--    amount           = 합계 금액 (NUMERIC(20,4))
--    item_count       = 항목 수 (>= 0)
----------------------------------------------------------------------
CREATE TABLE sales_aggregates (
    id              UUID            PRIMARY KEY,
    aggregate_date  DATE            NOT NULL,
    partner_id      UUID            NOT NULL,
    amount          NUMERIC(20, 4)  NOT NULL,
    item_count      INT             NOT NULL,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 활성 행 한정 unique — 같은 날짜 / 거래처 중복 방지
CREATE UNIQUE INDEX ux_sales_aggregates_date_partner_active
    ON sales_aggregates (aggregate_date, partner_id)
    WHERE is_deleted = FALSE;

-- 날짜별 시계열 lookup
CREATE INDEX ix_sales_aggregates_date_active
    ON sales_aggregates (aggregate_date DESC)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 4) Materialized View — mv_realtime_stock_summary
--    창고별 SKU 수 + 총수량 합. REFRESH MATERIALIZED VIEW CONCURRENTLY 지원
--    (5분 간격 scheduled, admin trigger 가능 — D-P9-13).
----------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_realtime_stock_summary AS
    SELECT  warehouse_code,
            COUNT(DISTINCT product_id) AS sku_count,
            SUM(quantity)              AS total_quantity,
            MAX(refreshed_at)          AS latest_refreshed_at
    FROM    realtime_stocks
    WHERE   is_deleted = FALSE
    GROUP BY warehouse_code;

-- CONCURRENTLY refresh 를 위한 unique index 의무
CREATE UNIQUE INDEX mv_realtime_stock_summary_warehouse
    ON mv_realtime_stock_summary (warehouse_code);

----------------------------------------------------------------------
-- 5) Materialized View — mv_sales_daily_summary
--    일별 거래처 수 + 총금액 + 총항목수. CONCURRENTLY 지원.
----------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_sales_daily_summary AS
    SELECT  aggregate_date,
            COUNT(DISTINCT partner_id) AS partner_count,
            SUM(amount)                AS total_amount,
            SUM(item_count)            AS total_items
    FROM    sales_aggregates
    WHERE   is_deleted = FALSE
    GROUP BY aggregate_date;

CREATE UNIQUE INDEX mv_sales_daily_summary_date
    ON mv_sales_daily_summary (aggregate_date);
