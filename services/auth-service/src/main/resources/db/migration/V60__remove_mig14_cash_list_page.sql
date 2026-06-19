-- V60__remove_mig14_cash_list_page.sql
-- Remove folded-native cash transaction silo page-code.
-- CashDisbursement/CashReceipt lineage tables remain for MIG-7 import, MIG-9 journal generation,
-- and cross-check consumers; only the MIG-14 admin page permission surface is retired.

-- 1) Deprecated source grant table: hard delete.
DELETE FROM role_page_permissions WHERE page_code = 'ecount.mig14.cash-list';

-- 2) Enforcement/template tables: soft delete active rows.
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
