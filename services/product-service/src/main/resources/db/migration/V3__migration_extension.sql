-- V3__migration_extension.sql
-- Phase 6 M1a — legacy 시트 27탭 → 8 entity 마이그.
-- 출처: migration/analysis/04-migration-plan.md §2.1 + migration/decisions/DOMAIN-EXTENSIONS.md §1~§4
--
-- 본 마이그는 기존 V1 `products` 테이블(BaseEntity 7 audit fields 포함) 을 확장(ProductMaster 역할)
-- + 신규 7 entity (PriceHistory / BundleComponent / MaterialPrice / BranchPipeLookup /
--   OduRecommendationLookup / ProductSpec / SpecKeyTemplate) 추가.
--
-- 가드:
--   - BaseEntity 7 audit fields 의무 (created_at/by, modified_at/by, deleted_at/by, is_deleted)
--   - Soft Delete 단방향 (DOMAIN-EXTENSIONS §3 의 ProductSpec ON DELETE CASCADE 는 hard 가 아닌 PK 정합용)
--   - PostgreSQL CHECK 제약으로 enum 값 강제

-- ============================================================
-- 1) products (ProductMaster) 확장 — 10 신규 컬럼 + composite index
-- ============================================================
ALTER TABLE products
    ADD COLUMN model_code              VARCHAR(64),                                          -- 시트 B열 모델명 (사용자 노출 식별자, UUID 비공개)
    ADD COLUMN product_type            VARCHAR(16) NOT NULL DEFAULT 'SINGLE',                -- DOMAIN-EXTENSIONS §2 옵션 A
    ADD COLUMN bundle_mode             VARCHAR(16) NULL,                                     -- BUNDLE 인 경우만; SEND_AS_SET_IDS = KEEP
    ADD COLUMN has_variable_discount   BOOLEAN     NOT NULL DEFAULT FALSE,                   -- DOMAIN-EXTENSIONS §1 룰 1 (`$L$2` 절대참조)
    ADD COLUMN fixed_discount_rate     NUMERIC(5,4) NULL,                                    -- 룰 3 (구형 50%) + 행별 고정DC L 컬럼
    ADD COLUMN set_material_key        VARCHAR(2)  NULL,                                     -- 룰 2 (D4/D7/D8) — G8 확정
    ADD COLUMN legacy_discount_flag    BOOLEAN     NOT NULL DEFAULT FALSE,                   -- 구형 시트 41 row TRUE
    ADD COLUMN discount_flags          VARCHAR(20) NOT NULL DEFAULT '000000',                -- 6-bit (is360,is4way,is1way,isStand,isDeluxe,isGrade1)
    ADD COLUMN release_price           NUMERIC(12,2) NOT NULL DEFAULT 0,                     -- 시트 D/E 출고가 (베이스)
    ADD COLUMN delivery_price          NUMERIC(12,2) NOT NULL DEFAULT 0,                     -- 시트 F/G/H 납품가 (베이스)
    ADD COLUMN pyong_size              NUMERIC(5,2) NULL,                                    -- 싱글 세트 B열 평형
    ADD COLUMN product_category        VARCHAR(20) NULL,                                     -- HOME_MULTI/SINGLE_SET/SINGLE_PART/COMMERCIAL_MULTI/COMMERCIAL_PART/OLD/MATERIAL
    ADD COLUMN usage_scope             VARCHAR(16) NOT NULL DEFAULT 'NONE',                  -- DOMAIN-EXTENSIONS §3 — default NONE (미노출)
    ADD COLUMN estimate_category       VARCHAR(20) NULL,                                     -- HOME_MULTI/SINGLE_SET/COMMERCIAL_MULTI/LEGACY/OTHER
    ADD COLUMN spec_text               VARCHAR(255) NULL,                                    -- (legacy) 시트 규격 — ProductSpec 으로 대체 (read-only fallback)
    ADD COLUMN remark                  TEXT NULL,                                            -- 시트 비고
    ADD COLUMN parent_bundle_set_model VARCHAR(64) NULL                                      -- 싱글 구성품 M열 / 상업멀티 구성 I열 (sub-product)
;

-- enum CHECK 제약 (Plan §2.1.1 SQL)
ALTER TABLE products
    ADD CONSTRAINT chk_pm_product_type      CHECK (product_type IN ('SINGLE','BUNDLE')),
    ADD CONSTRAINT chk_pm_bundle_mode       CHECK (bundle_mode IS NULL OR bundle_mode IN ('EXPAND','KEEP')),
    ADD CONSTRAINT chk_pm_set_material_key  CHECK (set_material_key IS NULL OR set_material_key IN ('D4','D7','D8')),
    ADD CONSTRAINT chk_pm_usage_scope       CHECK (usage_scope IN ('NONE','ESTIMATE','PARTNER_ORDER','BOTH')),
    ADD CONSTRAINT chk_pm_estimate_category CHECK (estimate_category IS NULL OR estimate_category IN ('HOME_MULTI','SINGLE_SET','COMMERCIAL_MULTI','LEGACY','OTHER')),
    ADD CONSTRAINT chk_pm_product_category  CHECK (product_category IS NULL OR product_category IN ('HOME_MULTI','SINGLE_SET','SINGLE_PART','COMMERCIAL_MULTI','COMMERCIAL_PART','OLD','MATERIAL'));

-- index — 검색 성능 (DOMAIN-EXTENSIONS §3)
CREATE UNIQUE INDEX ux_products_model_code_active ON products(model_code) WHERE is_deleted = FALSE AND model_code IS NOT NULL;
CREATE INDEX ix_products_parent_set ON products(parent_bundle_set_model);
CREATE INDEX ix_products_usage_category ON products(usage_scope, estimate_category);

-- ============================================================
-- 2) price_history — 마스터 충돌 4건 해소 + 단가인상 2026-04-01 분기
-- ============================================================
CREATE TABLE price_history (
    id                  UUID         PRIMARY KEY,
    product_id          UUID         NOT NULL REFERENCES products(id),
    effective_date      DATE         NOT NULL,
    release_price       NUMERIC(12,2) NOT NULL,
    delivery_price      NUMERIC(12,2) NOT NULL,
    set_material_key    VARCHAR(2)   NULL,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_ph_set_material_key CHECK (set_material_key IS NULL OR set_material_key IN ('D4','D7','D8')),
    CONSTRAINT uq_ph_product_date UNIQUE (product_id, effective_date)
);
CREATE INDEX ix_ph_product_date ON price_history(product_id, effective_date);

-- ============================================================
-- 3) bundle_component — BUNDLE 부모 ↔ component 1:N
-- ============================================================
CREATE TABLE bundle_component (
    id                       UUID         PRIMARY KEY,
    bundle_product_id        UUID         NOT NULL REFERENCES products(id),
    component_product_code   VARCHAR(64)  NOT NULL,
    default_qty              NUMERIC(5,2) NOT NULL DEFAULT 1,
    qty_mode                 VARCHAR(16)  NOT NULL DEFAULT 'FIXED',
    component_kind           VARCHAR(16)  NOT NULL DEFAULT 'ACCESSORY',
    component_variant        VARCHAR(64)  NULL,
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    spec_text                VARCHAR(255) NULL,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_bc_qty_mode  CHECK (qty_mode IN ('FIXED','FOLLOW_SET')),
    CONSTRAINT chk_bc_kind      CHECK (component_kind IN ('INDOOR','OUTDOOR','PANEL','REMOTE','MATERIAL','ACCESSORY','FOOT'))
);
CREATE INDEX ix_bc_bundle ON bundle_component(bundle_product_id);
CREATE INDEX ix_bc_component_code ON bundle_component(component_product_code);

-- ============================================================
-- 4) material_price — D4/D7/D8 자재 단가 매트릭스
-- ============================================================
CREATE TABLE material_price (
    id                  UUID         PRIMARY KEY,
    material_key        VARCHAR(8)   NOT NULL,                         -- D2/D3/D4/.../D29 등
    name                VARCHAR(128) NOT NULL,
    price               NUMERIC(12,2) NOT NULL DEFAULT 0,
    option_label        VARCHAR(64)  NULL,
    computed_formula    TEXT         NULL,                             -- 시트 D 열 수식 보존
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_mp_material_key UNIQUE (material_key)
);

-- ============================================================
-- 5) branch_pipe_lookup — 분기관 99 row (G13 사용자 매핑 검토 후 실 시드)
-- ============================================================
CREATE TABLE branch_pipe_lookup (
    id                  UUID         PRIMARY KEY,
    branch_code         VARCHAR(16)  NOT NULL,                         -- 1509/2512/2812/3419 등
    description         VARCHAR(255) NULL,                             -- spot-check 결과
    summary_qty         INT          NULL,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_bp_branch_code UNIQUE (branch_code)
);

-- ============================================================
-- 6) odu_recommendation_lookup — 추천실외기 24 row
-- ============================================================
CREATE TABLE odu_recommendation_lookup (
    id                  UUID         PRIMARY KEY,
    recommendation_type VARCHAR(32)  NOT NULL,                         -- MULTI_HEATING_COOLING / HOME_MULTI
    indoor_capacity     NUMERIC(8,2) NOT NULL,
    indoor_count        INT          NULL,
    outdoor_hp          VARCHAR(8)   NOT NULL,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_odu_rec_type CHECK (recommendation_type IN ('MULTI_HEATING_COOLING','HOME_MULTI'))
);
CREATE INDEX ix_odu_type_cap ON odu_recommendation_lookup(recommendation_type, indoor_capacity);

-- ============================================================
-- 7) product_spec — 동적 스펙 1:N (DOMAIN-EXTENSIONS §4)
-- ============================================================
CREATE TABLE product_spec (
    id                  UUID         PRIMARY KEY,
    product_id          UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    spec_key            VARCHAR(50)  NOT NULL,
    spec_value          VARCHAR(255) NOT NULL,
    unit                VARCHAR(20)  NULL,
    display_order       INT          NOT NULL DEFAULT 0,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ps_product_key UNIQUE (product_id, spec_key)
);
CREATE INDEX ix_ps_product_order ON product_spec(product_id, display_order);

-- ============================================================
-- 8) spec_key_template — 카테고리별 추천 specKey (53 row 시드)
-- ============================================================
CREATE TABLE spec_key_template (
    id                  UUID         PRIMARY KEY,
    estimate_category   VARCHAR(20)  NOT NULL,
    spec_key            VARCHAR(50)  NOT NULL,
    default_unit        VARCHAR(20)  NULL,
    display_order       INT          NOT NULL DEFAULT 0,
    is_recommended      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- BaseEntity 7 audit fields
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_skt_category CHECK (estimate_category IN ('HOME_MULTI','SINGLE_SET','COMMERCIAL_MULTI','LEGACY','OTHER')),
    CONSTRAINT uq_skt_cat_key UNIQUE (estimate_category, spec_key)
);
CREATE INDEX ix_skt_cat_order ON spec_key_template(estimate_category, display_order);
