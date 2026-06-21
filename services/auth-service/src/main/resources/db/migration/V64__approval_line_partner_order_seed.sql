-- V64__approval_line_partner_order_seed.sql
-- A2-4 거래처 주문 출고전환 결재라인 역할 seed(action_key 직접 앵커).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

INSERT INTO approval_line_config
    (id, document_type, sequence, label, step_type, action_key, required, created_by)
SELECT gen_random_uuid(), v.document_type, v.sequence, v.label, v.step_type, v.action_key, TRUE, 'v64-seed'
FROM (VALUES
    ('PARTNER_ORDER', 0, '작성자', 'CREATOR', NULL),
    ('PARTNER_ORDER', 1, '승인자', 'GROUP', 'PARTNER_ORDER_CONVERT')
) AS v(document_type, sequence, label, step_type, action_key)
WHERE NOT EXISTS (
    SELECT 1
      FROM approval_line_config a
     WHERE a.document_type = v.document_type
       AND a.sequence = v.sequence
       AND a.is_deleted = FALSE
);
