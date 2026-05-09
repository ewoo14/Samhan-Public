-- V3__add_accounting_period.sql
-- Phase 10 Step 8 — P2-4 매출 마감 (일별/월별).
--
-- AccountingPeriod: OPEN → CLOSED (역마감 시 OPEN). 마감된 기간의 분개/슬립 변경 차단
-- (AccountingPeriodGuard interceptor). MASTER 만 reverse 가능.
--
-- period_type DAILY/MONTHLY. period_date 는 일별이면 해당 일자, 월별이면 해당 월의
-- 1일 (말일이 아닌 1일로 정규화 — 조회 단순화).

CREATE TABLE accounting_periods (
    id                       UUID            PRIMARY KEY,
    period_type              VARCHAR(20)     NOT NULL,
    period_date              DATE            NOT NULL,
    status                   VARCHAR(20)     NOT NULL,
    closed_at                TIMESTAMP,
    closed_by                VARCHAR(50),
    reversed_at              TIMESTAMP,
    reversed_by              VARCHAR(50),
    total_sales              NUMERIC(15,2)   NOT NULL DEFAULT 0,
    total_purchase           NUMERIC(15,2)   NOT NULL DEFAULT 0,
    total_expense            NUMERIC(15,2)   NOT NULL DEFAULT 0,
    locked_slip_count        INT             NOT NULL DEFAULT 0,
    description              VARCHAR(500),
    version                  BIGINT          NOT NULL DEFAULT 0,

    created_at               TIMESTAMP       NOT NULL,
    created_by               VARCHAR(50)     NOT NULL,
    modified_at              TIMESTAMP,
    modified_by              VARCHAR(50),
    deleted_at               TIMESTAMP,
    deleted_by               VARCHAR(50),
    is_deleted               BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 동일 (type, period_date) 의 OPEN/CLOSED 1건 보장. 역마감 후 재마감은 동일 row 재사용
-- (역마감 → status=OPEN → 재마감 → status=CLOSED).
CREATE UNIQUE INDEX ux_accounting_periods_type_date_active
    ON accounting_periods (period_type, period_date)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_accounting_periods_status_active
    ON accounting_periods (status, is_deleted);

CREATE INDEX ix_accounting_periods_period_date_active
    ON accounting_periods (period_date, is_deleted);
