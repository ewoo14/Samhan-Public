-- V58__seed_estimate_config_page_permission.sql
-- Formula builder Phase 1 — desktop sales estimate global pricing config.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

INSERT INTO role_page_permissions
    (id, role_code, page_code, can_view, can_edit, created_at, created_by, is_deleted)
VALUES
    (gen_random_uuid(), 'MASTER', 'sales.estimate-config', TRUE, TRUE, NOW(), 'system', FALSE),
    (gen_random_uuid(), 'MANAGER', 'sales.estimate-config', TRUE, TRUE, NOW(), 'system', FALSE)
ON CONFLICT DO NOTHING;
