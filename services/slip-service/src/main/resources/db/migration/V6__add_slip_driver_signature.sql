-- V6__add_slip_driver_signature.sql
-- Slip Service — Slice C2 (PR #23 follow-up):
-- 배송기사 서명 4 필드 추가 (Slip.recordDriverSignature 메서드용).
--
-- 인수자 서명 (V5) 와 동일 패턴 — share token 만 재사용 (별도 발급 X).
--
-- 라이프사이클:
--   recordDriverSignature(png, hash, channel) — INSPECTING/COMPLETED/SHIPPING 만 허용
--   audit log: action=RECORD_DRIVER (signer_name 컬럼에 driverName 기록)

ALTER TABLE slips ADD COLUMN driver_signed_at         TIMESTAMP;
ALTER TABLE slips ADD COLUMN driver_signature_png     BYTEA;
ALTER TABLE slips ADD COLUMN driver_signature_hash    VARCHAR(64);
ALTER TABLE slips ADD COLUMN driver_signature_channel VARCHAR(20);
