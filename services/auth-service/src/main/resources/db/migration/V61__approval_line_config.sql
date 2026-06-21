-- V61__approval_line_config.sql
-- A2-1 결재라인 설정 — 전표종류별 결재 역할 카탈로그(선언적) + 출고 seed + page-code grant.

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
    CONSTRAINT approval_line_config_pk PRIMARY KEY (id)
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
    (id, group_id, page_code, can_view, can_create, can_update, can_delete, can_restore, can_download, can_print,
     created_at, created_by, is_deleted)
SELECT gen_random_uuid(), roles.group_id, 'admin.approval-line-config',
       TRUE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, NOW(), 'v61-seed', FALSE
FROM (VALUES
    ('00000000-0000-0000-0000-000000000100'::uuid),  -- MASTER
    ('00000000-0000-0000-0000-000000000101'::uuid)   -- MANAGER
) AS roles(group_id)
WHERE NOT EXISTS (
    SELECT 1 FROM group_page_permissions g
    WHERE g.group_id = roles.group_id AND g.page_code = 'admin.approval-line-config' AND g.is_deleted = FALSE
);
