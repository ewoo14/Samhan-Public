-- V12__add_ecount_slipline_fields.sql
-- Slip Service — feature/local-test-setup Stage 2:
-- 이카운트 판매입력 라인 컬럼 매핑을 위한 SlipLine 신규 4 필드.
--   * unit_price_with_vat — VAT 포함 단가 (unit_price * 1.1)
--   * supply_amount       — 공급가액 (unit_price * quantity)
--   * vat_amount          — 부가세 (supply_amount * 0.1)
--   * unit_price_with_vat 는 모바일 판매입력 화면 "VAT포함단가" 컬럼 1:1 매핑
--
-- 모든 신규 컬럼은 NULL 허용 (기존 라인 row 호환). 신규 라인은 도메인 메서드에서 자동 계산.
-- 컬럼 타입 컨벤션 (V1 계승):
--   * 가격/금액 NUMERIC(15,2) — 라인 단가/공급가, 단일 NUMERIC(15,2) 로 통일 (line_total 만 NUMERIC(17,2) 곱셈 마진)

----------------------------------------------------------------------
-- 1) slip_lines — 이카운트 판매입력 4 필드 추가
----------------------------------------------------------------------
ALTER TABLE slip_lines ADD COLUMN unit_price_with_vat NUMERIC(15,2);
ALTER TABLE slip_lines ADD COLUMN supply_amount       NUMERIC(17,2);
ALTER TABLE slip_lines ADD COLUMN vat_amount          NUMERIC(15,2);
