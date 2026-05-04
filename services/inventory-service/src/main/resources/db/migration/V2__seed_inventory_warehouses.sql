-- V2__seed_inventory_warehouses.sql
-- Plan §3.1 의 4-tier 창고 모델 시드:
--   HQ-001 본사창고     (HEADQUARTERS) — 본사 보유 메인 창고
--   VH-001 1호차 차량재고 (VEHICLE)      — 출장 차량 이동 재고
--   CS-001 위탁창고      (CONSIGNMENT)  — 거래처 위탁 재고 (소유권 자사)
--   VR-001 가상창고      (VIRTUAL)      — 삼성 직배/서비스 인보이스 (IN_TRANSIT 스킵)
-- ID 는 deterministic UUID. system audit.

INSERT INTO warehouses (
    id, code, name, type, address, display_order, description,
    created_at, created_by, is_deleted
) VALUES
('11111111-1111-1111-1111-000000000001', 'HQ-001', '본사창고', 'HEADQUARTERS',
 '서울시 강남구 본사', 1, '본사 보유 메인 창고',
 CURRENT_TIMESTAMP, 'system', FALSE),
('11111111-1111-1111-1111-000000000002', 'VH-001', '1호차 차량재고', 'VEHICLE',
 NULL, 2, '출장 차량 이동 재고 (창고원/기사 단위)',
 CURRENT_TIMESTAMP, 'system', FALSE),
('11111111-1111-1111-1111-000000000003', 'CS-001', '거래처 위탁창고', 'CONSIGNMENT',
 NULL, 3, '거래처에 위탁한 재고 (소유권은 자사)',
 CURRENT_TIMESTAMP, 'system', FALSE),
('11111111-1111-1111-1111-000000000004', 'VR-001', '가상창고', 'VIRTUAL',
 NULL, 4, '삼성 직배/반품/서비스 인보이스 등 비물리 — IN_TRANSIT 스킵',
 CURRENT_TIMESTAMP, 'system', FALSE);
