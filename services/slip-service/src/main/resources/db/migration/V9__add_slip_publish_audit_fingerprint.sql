-- V9__add_slip_publish_audit_fingerprint.sql
-- Phase 6 M5 (slip-service-integration) 회귀 fix — idempotency replay 정확성.
--
-- 문제:
--   기존 audit 의 supplyAmount/vatAmount/dcSnapshot 합으로 만든 fingerprint 와
--   신규 요청 fingerprint (kind+ioDate+lines 등) 가 다른 알고리즘이라
--   같은 idempotency-key + 같은 본문도 항상 409 Conflict 로 fail.
--
-- 해결:
--   request_fingerprint VARCHAR(64) 컬럼 추가 (SHA-256 hex). 신규 발행 시 저장,
--   replay 시 동일 알고리즘으로 재계산하여 비교.

ALTER TABLE slip_publish_audit
    ADD COLUMN request_fingerprint VARCHAR(64);

COMMENT ON COLUMN slip_publish_audit.request_fingerprint
    IS '발행 요청 본문 SHA-256 fingerprint (idempotency replay 비교용)';
