-- V4__add_estimate_config.sql
-- Formula builder Phase 1 — estimate-app global pricing parameters.

CREATE TABLE estimate_configs (
    id                                UUID          PRIMARY KEY,
    singleton_key                     BOOLEAN       NOT NULL DEFAULT TRUE,
    common_home_discount_rate         NUMERIC(5,4)  NOT NULL DEFAULT 0.4500 CHECK (common_home_discount_rate >= 0 AND common_home_discount_rate < 1),
    common_commercial_discount_rate   NUMERIC(5,4)  NOT NULL DEFAULT 0.4500 CHECK (common_commercial_discount_rate >= 0 AND common_commercial_discount_rate < 1),
    old_product_discount_rate         NUMERIC(5,4)  NOT NULL DEFAULT 0.5000 CHECK (old_product_discount_rate >= 0 AND old_product_discount_rate < 1),
    vat_rate                          NUMERIC(5,4)  NOT NULL DEFAULT 0.1000 CHECK (vat_rate >= 0 AND vat_rate < 1),
    card_fee_rate                     NUMERIC(5,4)  NOT NULL DEFAULT 0.0300 CHECK (card_fee_rate >= 0 AND card_fee_rate < 1),
    advance_discount_rate             NUMERIC(5,4)  NOT NULL DEFAULT 0.0000 CHECK (advance_discount_rate >= 0 AND advance_discount_rate < 1),
    combo_warn_rate                   NUMERIC(5,4)  NOT NULL DEFAULT 0.0000 CHECK (combo_warn_rate >= 0 AND combo_warn_rate < 1),
    footer_notice                     TEXT,

    created_at                        TIMESTAMP     NOT NULL,
    created_by                        VARCHAR(50)   NOT NULL,
    modified_at                       TIMESTAMP,
    modified_by                       VARCHAR(50),
    deleted_at                        TIMESTAMP,
    deleted_by                        VARCHAR(50),
    is_deleted                        BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_estimate_configs_singleton_active
    ON estimate_configs (singleton_key)
    WHERE is_deleted = FALSE AND singleton_key = TRUE;

INSERT INTO estimate_configs (
    id,
    singleton_key,
    common_home_discount_rate,
    common_commercial_discount_rate,
    old_product_discount_rate,
    vat_rate,
    card_fee_rate,
    advance_discount_rate,
    combo_warn_rate,
    footer_notice,
    created_at,
    created_by,
    is_deleted
) VALUES (
    '00000000-0000-0000-0000-000000000004',
    TRUE,
    0.4500,
    0.4500,
    0.5000,
    0.1000,
    0.0300,
    0.0000,
    0.0000,
    '※ 분기관은 임의 산정입니다.
※ 견적 내용 확정 시 재고확인 요청 부탁드립니다.
※ 본 견적은 견적일로부터 30일 이내에만 유효합니다.
※ 공공기관 발주 현장의 경우 본 견적은 무효이며, 별도의 검토가 필요합니다.',
    NOW(),
    'system',
    FALSE
)
ON CONFLICT DO NOTHING;
