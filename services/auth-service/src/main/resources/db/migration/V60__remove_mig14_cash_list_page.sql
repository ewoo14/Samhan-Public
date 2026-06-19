-- V60__remove_mig14_cash_list_page.sql
-- 이카운트 네이티브 편입 슬2: 현금 지출/입금 silo 폐기.
-- page-code ecount.mig14.cash-list 를 권한 모델 전 테이블에서 제거한다(V59 패턴 동일).
-- 현금 자료는 MIG-9 가 이미 네이티브 journals(분개장/입금매칭/원장)에 편입해 노출 — silo 만 폐기.
-- CashDisbursement/CashReceipt 엔티티·repository·cash_* 테이블은 MIG-7 import / MIG-9 journal 생성 /
-- cross-check 소비자용 lineage 로 유지(물리 제거는 Phase11 cutover 후 D3).

-- 1) 레거시(DEPRECATED) 원본 시드 — hard delete
DELETE FROM role_page_permissions WHERE page_code = 'ecount.mig14.cash-list';

-- 2) enforcement/template 테이블 — soft delete (부분 unique uq_*_active WHERE is_deleted=FALSE)
UPDATE role_page_permission_templates
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v60-mig14-cash-removal'
 WHERE page_code = 'ecount.mig14.cash-list' AND is_deleted = FALSE;
UPDATE account_page_permissions
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v60-mig14-cash-removal'
 WHERE page_code = 'ecount.mig14.cash-list' AND is_deleted = FALSE;
UPDATE group_page_permissions
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v60-mig14-cash-removal'
 WHERE page_code = 'ecount.mig14.cash-list' AND is_deleted = FALSE;
UPDATE account_permission_overrides
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v60-mig14-cash-removal'
 WHERE page_code = 'ecount.mig14.cash-list' AND is_deleted = FALSE;
