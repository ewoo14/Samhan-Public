-- V20: 수식 빌더 F1-a — 견적 품목 Classification 마스터 + 품목별 L/M/S 분류.
-- 기존 row 안전: products 신규 FK 컬럼은 nullable, fixed_discount_rate 는 0~100 % 스케일로 변환.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS classification (
    id                UUID         PRIMARY KEY,
    estimate_category VARCHAR(20)  NOT NULL,
    cat_level         CHAR(1)      NOT NULL,
    parent_id         UUID         REFERENCES classification(id),
    name              VARCHAR(100) NOT NULL,
    display_order     INTEGER      NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    modified_at       TIMESTAMP,
    modified_by       VARCHAR(50),
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(50),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_classification_estimate_category'
    ) THEN
        ALTER TABLE classification
            ADD CONSTRAINT chk_classification_estimate_category
            CHECK (estimate_category IN ('HOME_MULTI','SINGLE_SET','COMMERCIAL_MULTI','LEGACY','OTHER'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_classification_cat_level'
    ) THEN
        ALTER TABLE classification
            ADD CONSTRAINT chk_classification_cat_level CHECK (cat_level IN ('L','M','S'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_classification_l_active
    ON classification (estimate_category, cat_level, name)
    WHERE is_deleted = FALSE AND parent_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_classification_child_active
    ON classification (estimate_category, cat_level, parent_id, name)
    WHERE is_deleted = FALSE AND parent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_classification_parent_order_active
    ON classification (parent_id, display_order)
    WHERE is_deleted = FALSE;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS cat_l_id UUID REFERENCES classification(id),
    ADD COLUMN IF NOT EXISTS cat_m_id UUID REFERENCES classification(id),
    ADD COLUMN IF NOT EXISTS cat_s_id UUID REFERENCES classification(id),
    ADD COLUMN IF NOT EXISTS classification_manual BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fixed_discount_manual BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 비율(0~1, NUMERIC(5,4))을 %(0~100)로 변환 + 타입 확장을 동시에 수행한다.
-- UPDATE ×100 후 ALTER 순서는 NUMERIC(5,4)에서 0.5×100=50 > max 9.9999 numeric overflow가 나므로,
-- ALTER ... USING 으로 타입 확장과 ×100 변환을 한 번에 적용해 overflow를 회피한다.
ALTER TABLE products
    ALTER COLUMN fixed_discount_rate TYPE NUMERIC(5,2)
    USING (CASE
        WHEN fixed_discount_rate IS NOT NULL AND fixed_discount_rate > 0 AND fixed_discount_rate <= 1
        THEN fixed_discount_rate * 100
        ELSE fixed_discount_rate
    END);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_fixed_discount_rate_pct'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_fixed_discount_rate_pct
            CHECK (fixed_discount_rate IS NULL OR fixed_discount_rate BETWEEN 0 AND 100);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_products_classification_l_active
    ON products (cat_l_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_products_classification_m_active
    ON products (cat_m_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_products_classification_s_active
    ON products (cat_s_id)
    WHERE is_deleted = FALSE;
