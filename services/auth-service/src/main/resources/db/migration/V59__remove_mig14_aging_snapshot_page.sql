-- V59__remove_mig14_aging_snapshot_page.sql
-- 이카운트 네이티브 편입 슬1: 잔액 스냅샷 silo 폐기.
-- page-code ecount.mig14.aging-snapshot 의 role_page_permissions 행 전체 제거.
-- 거래처 잔액은 네이티브 보고서 /accounting/reports/partner-aging (journals POSTED 직접 집계) 가 대체.
-- MV partner_aging_snapshot 은 lineage 로 유지(cutover 후 물리 제거).
DELETE FROM role_page_permissions WHERE page_code = 'ecount.mig14.aging-snapshot';
