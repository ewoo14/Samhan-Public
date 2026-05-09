-- V5__add_ecount_product_fields.sql
-- 이카운트 품목 마스터 + HVAC 특화 단가 6종 호환을 위한 Product 컬럼 보강.
-- 출처: docs/migration/ecount-reference/091955~092016 (품목 3 탭 캡처)
--
-- 가드:
--   - 모든 신규 컬럼 NULLable 또는 default (legacy data 마이그 호환)
--   - 단가는 NUMERIC(15,2) (대형 시스템에어컨 천만 원 단위 안전)
--   - HVAC 특화 단가 6종 = singlePrice/outdoorPrice/multi50Price/multi48Price/multi45Price/item35Price

ALTER TABLE products
    -- 품목 기본 탭 (091955)
    ADD COLUMN product_code           VARCHAR(20),                          -- 이카운트 품목코드 5자리 (01XXXX)
    ADD COLUMN specification          VARCHAR(255),                         -- 규격
    ADD COLUMN unit                   VARCHAR(20) NOT NULL DEFAULT 'EA',    -- 단위 (EA/SET/M)
    ADD COLUMN product_business_type  VARCHAR(20) NOT NULL DEFAULT '상품',   -- 품목구분 (상품/제품/원재료)
    ADD COLUMN inventory_qty_mgmt     BOOLEAN NOT NULL DEFAULT TRUE,         -- 수량관리 여부
    ADD COLUMN barcode                VARCHAR(20),                           -- 바코드 (EAN-13 13자리)
    ADD COLUMN vat_rate_on_sales      NUMERIC(5,4) NOT NULL DEFAULT 0.10,    -- 매출 부가세율 (10%)
    ADD COLUMN vat_rate_on_purchase   NUMERIC(5,4) NOT NULL DEFAULT 0.10,    -- 매입 부가세율 (10%)
    ADD COLUMN price_includes_vat     BOOLEAN NOT NULL DEFAULT TRUE,         -- VAT 포함 여부 (이카운트 default true)

    -- 수량 탭 (092016)
    ADD COLUMN safety_stock_qty       INT NOT NULL DEFAULT 0,                -- 안전재고
    ADD COLUMN lead_time_days         INT NOT NULL DEFAULT 7,                -- 조달기간 (일)
    ADD COLUMN min_order_unit         INT NOT NULL DEFAULT 1,                -- 최소주문수량
    ADD COLUMN purchase_source        VARCHAR(100),                          -- 구매처 (예: 삼성전자(주))

    -- 분류 (이카운트 분류1/분류2)
    ADD COLUMN product_group1         VARCHAR(50),                           -- 분류1 (Samsung 에어컨 등)
    ADD COLUMN product_group2         VARCHAR(50),                           -- 분류2 (벽걸이/스탠드/시스템 등)

    -- HVAC 특화 단가 6종 ⭐ (이카운트 단가 매트릭스 발견)
    ADD COLUMN inbound_price          NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 입고단가
    ADD COLUMN outbound_price         NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 출고단가
    ADD COLUMN single_price           NUMERIC(15,2) NOT NULL DEFAULT 0,      -- ⭐ 싱글 단일 거래
    ADD COLUMN outdoor_price          NUMERIC(15,2) NOT NULL DEFAULT 0,      -- ⭐ 실외기 (원형, 스탠드)
    ADD COLUMN multi_50_price         NUMERIC(15,2) NOT NULL DEFAULT 0,      -- ⭐ 멀티 (50% 할인)
    ADD COLUMN multi_48_price         NUMERIC(15,2) NOT NULL DEFAULT 0,      -- ⭐ 멀티 (48% 할인)
    ADD COLUMN multi_45_price         NUMERIC(15,2) NOT NULL DEFAULT 0,      -- ⭐ 멀티 (45% 할인)
    ADD COLUMN item_35_price          NUMERIC(15,2) NOT NULL DEFAULT 0       -- ⭐ 단품 (35% 할인)
;

-- 검색 보조 인덱스
CREATE UNIQUE INDEX ux_products_product_code_active ON products (product_code)
    WHERE is_deleted = FALSE AND product_code IS NOT NULL;
CREATE INDEX ix_products_product_group1 ON products (product_group1)
    WHERE is_deleted = FALSE AND product_group1 IS NOT NULL;
CREATE INDEX ix_products_product_group2 ON products (product_group2)
    WHERE is_deleted = FALSE AND product_group2 IS NOT NULL;
CREATE INDEX ix_products_barcode ON products (barcode)
    WHERE is_deleted = FALSE AND barcode IS NOT NULL;

-- 부가세율 / 단위 가드
ALTER TABLE products
    ADD CONSTRAINT chk_products_vat_sales    CHECK (vat_rate_on_sales >= 0 AND vat_rate_on_sales <= 1),
    ADD CONSTRAINT chk_products_vat_purchase CHECK (vat_rate_on_purchase >= 0 AND vat_rate_on_purchase <= 1),
    ADD CONSTRAINT chk_products_unit         CHECK (unit IN ('EA','SET','M','BOX','KG'));
