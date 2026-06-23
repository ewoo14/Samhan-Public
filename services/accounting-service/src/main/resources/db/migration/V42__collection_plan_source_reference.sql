-- V42__collection_plan_source_reference.sql
-- G-2 자동제안 출처키 영속화 및 중복 등록 방지.
--
-- V41 은 이미 적용된 마이그레이션으로 간주하고 변경하지 않는다.

ALTER TABLE collection_plan
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(100);

COMMENT ON COLUMN collection_plan.source_reference IS
    '자동제안 출처키. RECEIVABLE_BALANCE=계정코드, NOTE_MATURITY=받을어음 번호, MANUAL=null';

CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_plan_source_active
    ON collection_plan (partner_id, basis, source_reference)
    WHERE is_deleted = FALSE
      AND source_reference IS NOT NULL
      AND status IN ('PLANNED', 'OVERDUE');
