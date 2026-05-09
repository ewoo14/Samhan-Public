-- V4__seed_inventory_audit_accounts.sql
-- Phase 10 Step 8 — P2-6 재고 실사 차이 자동 분개 호환 시드.
--
-- inventory-service AccountingClient 가 차이 분개 발행 시 사용하는 한국 일반기업회계기준
-- 표준 계정과목 2건 추가:
--
--   * 150 재고자산 — V1 의 130 상품과 별개로 재고 일반 자산 계정 (2009-개정 표준 코드).
--                   inventory 차이 분개의 차변/대변 통합 계정으로 사용.
--   * 919 재고감모손실 — 영업외비용. 차이 (+) 환입 / (-) 손실 양방향 사용.
--
-- BaseEntity audit 컬럼은 SYSTEM seed 표시. is_leaf TRUE.
-- display_order 는 V1 의 (130 상품=1300) / (900-시작=9000) sequence 와 충돌 없도록 1500/9190 부여.

INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('150',  '재고자산',         'ASSET',         '100', TRUE, 1500, CURRENT_TIMESTAMP, 'SYSTEM'),
('919',  '재고감모손실',     'NON_OPERATING', '900', TRUE, 9190, CURRENT_TIMESTAMP, 'SYSTEM');
