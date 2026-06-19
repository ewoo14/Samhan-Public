-- V59__remove_mig14_aging_snapshot_page.sql
-- 이카운트 네이티브 편입 슬1: 잔액 스냅샷 silo 폐기.
-- page-code ecount.mig14.aging-snapshot 를 권한 모델 전 테이블에서 제거한다.
-- V39 개편으로 role_page_permissions(DEPRECATED) → role_page_permission_templates / account_page_permissions,
-- V42/V43 → group_page_permissions(현 enforcement 진실원, EffectivePermissionMaterializer 소비) 로 전파되었으므로
-- 전 테이블을 정리해야 orphan grant 재materialize 를 막는다.
-- 거래처 잔액은 네이티브 보고서 /accounting/reports/partner-aging (journals POSTED 직접 집계) 가 대체.
-- MV partner_aging_snapshot + Mig9AgingSnapshotRefreshService 는 lineage 로 유지(cutover 후 물리 제거).

-- 1) 레거시(DEPRECATED) 원본 시드 — hard delete
DELETE FROM role_page_permissions WHERE page_code = 'ecount.mig14.aging-snapshot';

-- 2) enforcement/template 테이블 — soft delete (부분 unique uq_*_active WHERE is_deleted=FALSE)
UPDATE role_page_permission_templates
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v59-mig14-aging-removal'
 WHERE page_code = 'ecount.mig14.aging-snapshot' AND is_deleted = FALSE;
UPDATE account_page_permissions
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v59-mig14-aging-removal'
 WHERE page_code = 'ecount.mig14.aging-snapshot' AND is_deleted = FALSE;
UPDATE group_page_permissions
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v59-mig14-aging-removal'
 WHERE page_code = 'ecount.mig14.aging-snapshot' AND is_deleted = FALSE;
UPDATE account_permission_overrides
   SET is_deleted = TRUE, deleted_at = NOW(), deleted_by = 'v59-mig14-aging-removal'
 WHERE page_code = 'ecount.mig14.aging-snapshot' AND is_deleted = FALSE;
