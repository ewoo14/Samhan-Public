-- V21: F1.5 수식 빌더 — Product 판넬/리모컨 attribute 적재용 nullable 컬럼.
-- parity-safe: 견적 출력/BundleExpander/FE 로직은 이 컬럼을 아직 소비하지 않는다.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS panel_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS remote_type VARCHAR(32);

CREATE INDEX IF NOT EXISTS ix_products_panel_type_active
    ON products (panel_type)
    WHERE is_deleted = FALSE AND panel_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_products_remote_type_active
    ON products (remote_type)
    WHERE is_deleted = FALSE AND remote_type IS NOT NULL;
