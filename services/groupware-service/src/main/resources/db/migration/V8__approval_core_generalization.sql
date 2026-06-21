-- V8__approval_core_generalization.sql
-- A1 공통 결재 엔진 일반화 — approval-core 베이스 상속에 필요한 additive 컬럼.
-- 원칙: 기존 행 무손상. 비결정적 결재자그룹/실승인자/서명 컬럼은 NOT NULL 절대 금지.
--       step_type 만 기존 행=USER 로 결정적 backfill 후 NOT NULL.

-- 1) approval_lines — 전표 연계(loose ref). 독립형 결재는 NULL.
ALTER TABLE approval_lines ADD COLUMN IF NOT EXISTS document_type VARCHAR(40);
ALTER TABLE approval_lines ADD COLUMN IF NOT EXISTS document_id   UUID;

-- 2) approval_steps — step 모델 일반화 컬럼(전부 nullable ADD).
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS step_type            VARCHAR(20);
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS approver_group_id    UUID;
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS required_page_code   VARCHAR(100);
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS approved_by_user_id  UUID;
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS signature_png_snapshot BYTEA;
ALTER TABLE approval_steps ADD COLUMN IF NOT EXISTS signed_at            TIMESTAMP;

-- step_type 결정적 backfill — 기존 그룹웨어 단계는 전부 USER(특정 사원 직접 지정).
UPDATE approval_steps SET step_type = 'USER' WHERE step_type IS NULL;

-- USER 모드 기존 처리완료 단계는 결재자=실승인자이므로 감사 컬럼을 의미 보존 백필.
UPDATE approval_steps
SET approved_by_user_id = approver_id
WHERE approved_by_user_id IS NULL
  AND status IN ('APPROVED', 'REJECTED');

ALTER TABLE approval_steps ALTER COLUMN step_type SET NOT NULL;
