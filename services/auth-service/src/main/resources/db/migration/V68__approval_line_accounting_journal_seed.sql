-- V68__approval_line_accounting_journal_seed.sql
-- 회계전표 게시 B-게이트 결재라인 역할 seed(action_key 직접 앵커).
-- approval_line_approver 는 미시드하여 approver 지정 전 configured=false(opt-in)를 유지한다.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

INSERT INTO approval_line_config
    (id, document_type, sequence, label, step_type, action_key, required, created_by)
SELECT gen_random_uuid(), v.document_type, v.sequence, v.label, v.step_type, v.action_key, TRUE, 'v68-seed'
FROM (VALUES
    ('ACCOUNTING_JOURNAL', 0, '작성자', 'CREATOR', NULL),
    ('ACCOUNTING_JOURNAL', 1, '결재자', 'GROUP', 'JOURNAL_POST')
) AS v(document_type, sequence, label, step_type, action_key)
-- per-row 멱등 가드(document_type, sequence) — V61 SLIP_OUTBOUND 패턴. 부분 삽입 복구 가능.
WHERE NOT EXISTS (
    SELECT 1
      FROM approval_line_config a
     WHERE a.document_type = v.document_type
       AND a.sequence = v.sequence
       AND a.is_deleted = FALSE
);
