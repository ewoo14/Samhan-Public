-- V18__add_product_estimate_exposure.sql
-- 품목 견적 카탈로그 노출을 products.estimate_category/display_order 단일 컬럼에서
-- product_estimate_exposure M:N 단일 원천으로 이관한다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE product_estimate_exposure (
    id                UUID         PRIMARY KEY,
    product_id        UUID         NOT NULL REFERENCES products(id),
    estimate_category VARCHAR(20)  NOT NULL,
    display_order     INTEGER,
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    modified_at       TIMESTAMP,
    modified_by       VARCHAR(50),
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(50),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_pee_category CHECK (estimate_category IN ('HOME_MULTI','SINGLE_SET','COMMERCIAL_MULTI','LEGACY','OTHER'))
);

CREATE UNIQUE INDEX ux_pee_product_category_active
    ON product_estimate_exposure (product_id, estimate_category)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_pee_category_order_active
    ON product_estimate_exposure (estimate_category, display_order)
    WHERE is_deleted = FALSE;

-- 백필: 기존 단일 estimate_category/display_order 를 활성 M:N 노출 1행으로 보존한다.
-- products 의 두 컬럼은 롤백 안전을 위해 남기지만, 신규 코드에서는 읽거나 쓰지 않는다.
INSERT INTO product_estimate_exposure (
    id, product_id, estimate_category, display_order, created_at, created_by, is_deleted
)
SELECT gen_random_uuid(), p.id, p.estimate_category, p.display_order, now(), 'V18_MIGRATION', FALSE
  FROM products p
 WHERE p.estimate_category IS NOT NULL
   AND p.is_deleted = FALSE
   AND NOT EXISTS (
       SELECT 1
         FROM product_estimate_exposure e
        WHERE e.product_id = p.id
          AND e.estimate_category = p.estimate_category
          AND e.is_deleted = FALSE
   );
