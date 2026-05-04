-- V2__add_slip_signature_and_inspecting.sql
-- Slip Service — Slice A (sales-polish-2): SlipStatus.INSPECTING 신규 단계 +
-- Slip 출고인/검수인 자동 기입 4 필드 + SlipLine.specification 신규 필드.
--
-- 사용자 피드백 #4 (규격 입력) + #9 (출고인/검수인 자동 서명) 처리.
--
-- 컬럼 타입 컨벤션 (V1 계승):
--   * 짧은 문자열 VARCHAR(N), CHAR/bpchar 금지
--   * timestamp 는 TIMESTAMP (timezone naive)
--   * 신규 필드는 모두 nullable — 기존 ACCEPTED/INSPECTING 미도달 전표 호환
--
-- INSPECTING enum 값은 status VARCHAR(20) 컬럼에 그대로 들어가므로 schema 변경 불필요.

----------------------------------------------------------------------
-- 1) slips — Slice A 자동 서명 4 필드 (사용자 피드백 #9)
----------------------------------------------------------------------
ALTER TABLE slips ADD COLUMN dispatcher_user_id   VARCHAR(50);
ALTER TABLE slips ADD COLUMN dispatcher_signed_at TIMESTAMP;
ALTER TABLE slips ADD COLUMN inspector_user_id    VARCHAR(50);
ALTER TABLE slips ADD COLUMN inspector_signed_at  TIMESTAMP;

----------------------------------------------------------------------
-- 2) slip_lines — Slice A 규격 필드 (사용자 피드백 #4)
----------------------------------------------------------------------
ALTER TABLE slip_lines ADD COLUMN specification VARCHAR(50);

----------------------------------------------------------------------
-- 3) Optional partial indexes — 결재란 / 검수자 lookup 가속화
----------------------------------------------------------------------
CREATE INDEX ix_slips_dispatcher_active
    ON slips (dispatcher_user_id)
    WHERE is_deleted = FALSE AND dispatcher_user_id IS NOT NULL;

CREATE INDEX ix_slips_inspector_active
    ON slips (inspector_user_id)
    WHERE is_deleted = FALSE AND inspector_user_id IS NOT NULL;
