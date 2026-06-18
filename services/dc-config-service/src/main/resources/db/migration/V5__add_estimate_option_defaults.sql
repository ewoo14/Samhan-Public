-- V5__add_estimate_option_defaults.sql
-- Formula builder F3 — estimate-app option defaults promoted from sheet Row2.

ALTER TABLE estimate_configs
    ADD COLUMN home_no_hose BOOLEAN DEFAULT FALSE,
    ADD COLUMN home_no_branch BOOLEAN DEFAULT FALSE,
    ADD COLUMN home_with_foot BOOLEAN DEFAULT FALSE,
    ADD COLUMN home_default_panel VARCHAR(64) DEFAULT '',
    ADD COLUMN single_default_wired_remote VARCHAR(64) DEFAULT '',
    ADD COLUMN single_no_remote BOOLEAN DEFAULT FALSE,
    ADD COLUMN single_with_base BOOLEAN DEFAULT FALSE,
    ADD COLUMN single_default_panel VARCHAR(64) DEFAULT '',
    ADD COLUMN single_panel_shape VARCHAR(16) DEFAULT '원형',
    ADD COLUMN single_discount NUMERIC(14,2) DEFAULT 0,
    ADD COLUMN single_one_way_discount NUMERIC(14,2) DEFAULT 0,
    ADD COLUMN single_material_inclusion VARCHAR(16) DEFAULT '별도';

UPDATE estimate_configs
SET home_no_hose = FALSE,
    home_no_branch = FALSE,
    home_with_foot = FALSE,
    home_default_panel = '',
    single_default_wired_remote = '',
    single_no_remote = FALSE,
    single_with_base = FALSE,
    single_default_panel = '',
    single_panel_shape = '원형',
    single_discount = 0,
    single_one_way_discount = 0,
    single_material_inclusion = '별도'
WHERE singleton_key = TRUE
  AND is_deleted = FALSE;
