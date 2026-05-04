-- V3__add_slip_driver_contact.sql
-- Slip Service — Slice B (notification-slice-B Plan §5.1):
-- 출고 슬립의 배송 기사 정보 (driverName/driverPhone) + DeliveryBatch FK 추가.
--
-- 모든 신규 컬럼은 nullable — 기존 슬립 호환 (driverName/Phone 없이도 정상 동작).
-- DeliveryBatch FK constraint 는 V4 에서 batches 테이블 생성 후 추가.
--
-- 컬럼 타입 컨벤션 (V1/V2 계승):
--   * 짧은 문자열 VARCHAR(N), CHAR/bpchar 금지
--   * driverPhone 은 VARCHAR(20) — 한국 휴대폰 패턴 ({@code 010-XXXX-XXXX}) 검증은 FE/도메인 책임

----------------------------------------------------------------------
-- 1) slips — Slice B 배송 기사 3 컬럼 (Plan §3.1)
----------------------------------------------------------------------
ALTER TABLE slips ADD COLUMN driver_name        VARCHAR(50);
ALTER TABLE slips ADD COLUMN driver_phone       VARCHAR(20);
ALTER TABLE slips ADD COLUMN delivery_batch_id  UUID;

----------------------------------------------------------------------
-- 2) Partial index — DeliveryBatch lookup 가속화 (배치 상세 + 공개 모바일 페이지)
----------------------------------------------------------------------
CREATE INDEX ix_slips_delivery_batch
    ON slips (delivery_batch_id)
    WHERE delivery_batch_id IS NOT NULL;

----------------------------------------------------------------------
-- 3) Partial index — 자동 그룹화 candidate set lookup
--    {@code DeliveryBatchService.autoGroupByDate} 가 (driverPhone, slipDate) 기준 조회
----------------------------------------------------------------------
CREATE INDEX ix_slips_driver_phone_date
    ON slips (driver_phone, slip_date)
    WHERE driver_phone IS NOT NULL;
