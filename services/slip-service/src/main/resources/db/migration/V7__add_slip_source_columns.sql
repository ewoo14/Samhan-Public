-- V7__add_slip_source_columns.sql
-- Phase 6 M5 (slip-service-integration) — Slip 발행 출처 + idempotency 3 컬럼 추가.
--
-- 설계: docs/migration/phase6/M5-slip-service-integration.md §3 (payload 매핑) +
--       CONSISTENCY-MATRIX (Sync REST 채택 + idempotency 3중 격리).
--
-- 3중 격리:
--   1) DB partial UNIQUE INDEX (본 파일) — 동일 idempotencyKey 동시 INSERT 차단
--   2) Circuit Breaker (서비스 레이어) — 같은 키 재시도 시 기존 slipNo 반환 (200) /
--      본문 mismatch 면 409 Conflict
--   3) Outbox (별도 슬라이스) — async event 재발행 보호
--
-- 컬럼 의미:
--   source_type      — ESTIMATE/PARTNER_ORDER/MANUAL/MIGRATED_ECOUNT (default MANUAL)
--   source_id        — estimateNumber 또는 partnerOrderId 등 비즈니스 식별자
--   idempotency_key  — 호출자 발급 키 (Idempotency-Key 헤더), nullable

ALTER TABLE slips ADD COLUMN source_type     VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE slips ADD COLUMN source_id       VARCHAR(64);
ALTER TABLE slips ADD COLUMN idempotency_key VARCHAR(128);

-- partial UNIQUE INDEX — idempotencyKey 발급된 슬립만 유일성 강제.
-- soft-delete 된 슬립은 제외 (재발행 가능).
CREATE UNIQUE INDEX uq_slips_idem_key
    ON slips (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = FALSE;

-- (sourceType, sourceId) 복합 인덱스 — GET /by-source endpoint 조회 + 회계 cross-check.
CREATE INDEX idx_slips_source
    ON slips (source_type, source_id);
