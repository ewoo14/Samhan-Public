-- V2__seed_org_chart.sql
-- Departments only — employees are seeded at runtime by OrgChartSeeder.java
-- (so the corresponding accounts can also be provisioned in auth-service via the
-- internal endpoints with a hashed password).

INSERT INTO departments (id, code, name, display_order, created_at, created_by, is_deleted) VALUES
    ('00000000-0000-0000-0000-000000000001', 'EXEC',       '대표실',  1, NOW(), 'system', FALSE),
    ('00000000-0000-0000-0000-000000000002', 'SALES_1',    '영업1팀', 2, NOW(), 'system', FALSE),
    ('00000000-0000-0000-0000-000000000003', 'SALES_2',    '영업2팀', 3, NOW(), 'system', FALSE),
    ('00000000-0000-0000-0000-000000000004', 'SALES_3',    '영업3팀', 4, NOW(), 'system', FALSE),
    ('00000000-0000-0000-0000-000000000005', 'ACCOUNTING', '회계팀',  5, NOW(), 'system', FALSE);
