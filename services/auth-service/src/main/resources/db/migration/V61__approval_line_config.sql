-- V61__approval_line_config.sql
-- A2-1 결재라인 설정 — 전표종류별 결재 역할 카탈로그(선언적) + 출고 seed + page-code grant.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS approval_line_config (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    document_type       VARCHAR(40)  NOT NULL,
    sequence            INT          NOT NULL,
    label               VARCHAR(50)  NOT NULL,
    step_type           VARCHAR(20)  NOT NULL,
    approver_group_id   UUID,
    required            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(50)  NOT NULL DEFAULT 'system',
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT approval_line_config_pk PRIMARY KEY (id),
    CONSTRAINT approval_line_config_step_type_chk CHECK (step_type IN ('CREATOR','GROUP','USER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_line_config_doctype_seq_active
    ON approval_line_config (document_type, sequence)
    WHERE is_deleted = FALSE;

-- 출고전표 기본 결재 역할 seed (권한 그룹 미지정 — 메뉴에서 MASTER 가 지정)
INSERT INTO approval_line_config (id, document_type, sequence, label, step_type, required, created_by)
SELECT gen_random_uuid(), v.document_type, v.sequence, v.label, v.step_type, TRUE, 'v61-seed'
FROM (VALUES
    ('SLIP_OUTBOUND', 0, '작성자', 'CREATOR'),
    ('SLIP_OUTBOUND', 1, '출고인', 'GROUP'),
    ('SLIP_OUTBOUND', 2, '검수인', 'GROUP')
) AS v(document_type, sequence, label, step_type)
WHERE NOT EXISTS (
    SELECT 1 FROM approval_line_config a
    WHERE a.document_type = v.document_type AND a.sequence = v.sequence AND a.is_deleted = FALSE
);

-- admin.approval-line-config page-code 를 MASTER/MANAGER 기본 그룹에 부여(VIEW+UPDATE)
INSERT INTO group_page_permissions
    (id, group_id, page_code,
     can_view, can_create, can_update, can_delete, can_restore, can_download, can_print,
     created_at, created_by, modified_at, modified_by, is_deleted)
SELECT
    gen_random_uuid(),
    roles.group_id,
    'admin.approval-line-config',
    TRUE,
    FALSE,
    TRUE,
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    NOW(),
    'v61-seed',
    NOW(),
    'v61-seed',
    FALSE
FROM (VALUES
    ('00000000-0000-0000-0000-000000000100'::uuid),  -- MASTER
    ('00000000-0000-0000-0000-000000000101'::uuid)   -- MANAGER
) AS roles(group_id)
ON CONFLICT (group_id, page_code) WHERE is_deleted = FALSE DO UPDATE
SET can_view = EXCLUDED.can_view,
    can_create = EXCLUDED.can_create,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete,
    can_restore = EXCLUDED.can_restore,
    can_download = EXCLUDED.can_download,
    can_print = EXCLUDED.can_print,
    modified_at = NOW(),
    modified_by = 'v61-seed';

-- account_page_permissions — 그룹 배속 계정 enforcement 캐시 동기화. 시스템마스터 제외.
INSERT INTO account_page_permissions
    (id, account_id, page_code,
     can_view, can_create, can_update, can_delete, can_restore, can_download, can_print,
     created_at, created_by, modified_at, modified_by, is_deleted)
SELECT
    gen_random_uuid(),
    ag.account_id,
    gpp.page_code,
    BOOL_OR(gpp.can_view),
    BOOL_OR(gpp.can_create),
    BOOL_OR(gpp.can_update),
    BOOL_OR(gpp.can_delete),
    BOOL_OR(gpp.can_restore),
    BOOL_OR(gpp.can_download),
    BOOL_OR(gpp.can_print),
    NOW(),
    'v61-seed',
    NOW(),
    'v61-seed',
    FALSE
FROM account_groups ag
JOIN accounts a
  ON a.id = ag.account_id
 AND a.is_deleted = FALSE
 AND a.enabled = TRUE
JOIN group_page_permissions gpp
  ON gpp.group_id = ag.group_id
 AND gpp.is_deleted = FALSE
 AND gpp.page_code = 'admin.approval-line-config'
WHERE ag.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM account_groups sg
      JOIN permission_groups pg
        ON pg.id = sg.group_id
       AND pg.is_deleted = FALSE
       AND pg.is_system_master = TRUE
      WHERE sg.account_id = ag.account_id
        AND sg.is_deleted = FALSE
  )
GROUP BY ag.account_id, gpp.page_code
ON CONFLICT (account_id, page_code) WHERE is_deleted = FALSE DO UPDATE
SET can_view = EXCLUDED.can_view,
    can_create = EXCLUDED.can_create,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete,
    can_restore = EXCLUDED.can_restore,
    can_download = EXCLUDED.can_download,
    can_print = EXCLUDED.can_print,
    modified_at = NOW(),
    modified_by = 'v61-seed';
