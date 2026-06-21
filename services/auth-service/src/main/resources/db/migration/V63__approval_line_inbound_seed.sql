-- V63__approval_line_inbound_seed.sql
-- A2-3 입고전표 결재라인 역할 seed(action_key 직접 앵커).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

INSERT INTO approval_line_config
    (id, document_type, sequence, label, step_type, action_key, required, created_by)
SELECT gen_random_uuid(), v.document_type, v.sequence, v.label, v.step_type, v.action_key, TRUE, 'v63-seed'
FROM (VALUES
    ('SLIP_INBOUND', 0, '작성자', 'CREATOR', NULL),
    ('SLIP_INBOUND', 1, '입고인', 'GROUP', 'INBOUND_RECEIVE'),
    ('SLIP_INBOUND', 2, '검수인', 'GROUP', 'INBOUND_INSPECT')
) AS v(document_type, sequence, label, step_type, action_key)
WHERE NOT EXISTS (
    SELECT 1
      FROM approval_line_config a
     WHERE a.document_type = v.document_type
       AND a.sequence = v.sequence
       AND a.is_deleted = FALSE
);
