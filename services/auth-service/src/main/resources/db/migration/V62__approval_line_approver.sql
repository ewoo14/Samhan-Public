-- V62__approval_line_approver.sql
-- A2-1c 결재 역할별 다중 결재자(그룹 + 개인) + action_key 앵커.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

ALTER TABLE approval_line_config
    ADD COLUMN IF NOT EXISTS action_key VARCHAR(40);

-- action_key 안정 앵커 seed (A2-2 enforcement 가 accept/inspect 를 이걸로 매핑).
-- ⚠️ label 이 아니라 sequence 순서로 매핑 — A2-1b 라벨 rename(출고인→출고담당) 후에도 앵커 생존.
--    SLIP_OUTBOUND 의 GROUP 역할은 정확히 2개(출고인=seq1, 검수인=seq2). sequence ASC 순서로
--    첫째=OUTBOUND_DISPATCH, 둘째=OUTBOUND_INSPECT. rename 은 sequence 불변이므로 무관.
--    한계: V62 적용 전 두 역할 sequence 를 swap(A2-1b reorder)한 DB 는 매핑이 뒤바뀜 — 신규 배포
--    (V61 seed 직후 V62)는 무관하며, edited DB cutover 시 action_key 점검 필요(스펙 박제).
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY sequence ASC) AS rn
      FROM approval_line_config
     WHERE document_type = 'SLIP_OUTBOUND'
       AND step_type = 'GROUP'
       AND is_deleted = FALSE
)
UPDATE approval_line_config c
   SET action_key = CASE r.rn WHEN 1 THEN 'OUTBOUND_DISPATCH'
                              WHEN 2 THEN 'OUTBOUND_INSPECT' END,
       modified_at = NOW(),
       modified_by = 'v62-seed'
  FROM ranked r
 WHERE c.id = r.id
   AND r.rn <= 2;

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
