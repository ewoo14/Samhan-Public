-- V62__approval_line_approver.sql
-- A2-1c 결재 역할별 다중 결재자(그룹 + 개인) + action_key 앵커.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

ALTER TABLE approval_line_config
    ADD COLUMN IF NOT EXISTS action_key VARCHAR(40);

UPDATE approval_line_config
   SET action_key = 'OUTBOUND_DISPATCH',
       modified_at = NOW(),
       modified_by = 'v62-seed'
 WHERE document_type = 'SLIP_OUTBOUND'
   AND step_type = 'GROUP'
   AND label = '출고인'
   AND is_deleted = FALSE;

UPDATE approval_line_config
   SET action_key = 'OUTBOUND_INSPECT',
       modified_at = NOW(),
       modified_by = 'v62-seed'
 WHERE document_type = 'SLIP_OUTBOUND'
   AND step_type = 'GROUP'
   AND label = '검수인'
   AND is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS approval_line_approver (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    config_role_id      UUID         NOT NULL,
    approver_type       VARCHAR(10)  NOT NULL,
    approver_ref_id     UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(50)  NOT NULL DEFAULT 'system',
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT approval_line_approver_pk PRIMARY KEY (id),
    CONSTRAINT approval_line_approver_type_chk CHECK (approver_type IN ('GROUP','USER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_line_approver_active
    ON approval_line_approver (config_role_id, approver_type, approver_ref_id)
    WHERE is_deleted = FALSE;

INSERT INTO approval_line_approver
    (id, config_role_id, approver_type, approver_ref_id,
     created_at, created_by, modified_at, modified_by, deleted_at, deleted_by, is_deleted)
SELECT
    gen_random_uuid(),
    id,
    'GROUP',
    approver_group_id,
    NOW(),
    'v62-seed',
    NOW(),
    'v62-seed',
    NULL,
    NULL,
    FALSE
FROM approval_line_config
WHERE approver_group_id IS NOT NULL
  AND is_deleted = FALSE
ON CONFLICT DO NOTHING;
