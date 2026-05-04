-- V1__init_product_service.sql
-- Product Service — initial schema for `categories` (자기참조 트리) + `products` (plan §3.5).
-- BaseEntity audit columns mirror user-service.V1__init_user_service.sql 정확히.
-- Soft-delete 는 application-side 의 @SQLRestriction("is_deleted = false") 로 강제.
-- 단종은 별도 status 컬럼으로 soft-delete 와 직교 운용 (개발책임자 결재).
-- 가격은 NUMERIC(15,2), 통화는 CHAR(3) 'KRW' default. 태그는 jsonb + GIN 인덱스.

CREATE TABLE categories (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    parent_id       UUID         REFERENCES categories(id),
    display_order   INT          NOT NULL DEFAULT 0,

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_categories_code_active
    ON categories (code)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_categories_parent_active
    ON categories (parent_id, is_deleted);

CREATE TABLE products (
    id              UUID           PRIMARY KEY,
    name            VARCHAR(150)   NOT NULL,
    model_name      VARCHAR(100)   NOT NULL,
    category_id     UUID           NOT NULL REFERENCES categories(id),
    selling_price   NUMERIC(15,2)  NOT NULL CHECK (selling_price  >= 0),
    purchase_price  NUMERIC(15,2)  NOT NULL CHECK (purchase_price >= 0),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'KRW',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    tags            JSONB,
    description     VARCHAR(1000),

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP      NOT NULL,
    created_by      VARCHAR(50)    NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_products_model_name_active
    ON products (model_name)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_products_category_active
    ON products (category_id, is_deleted);

CREATE INDEX ix_products_status_active
    ON products (status, is_deleted);

CREATE INDEX gin_products_tags
    ON products USING gin (tags jsonb_path_ops);
