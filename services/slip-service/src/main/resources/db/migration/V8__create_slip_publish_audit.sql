-- V8__create_slip_publish_audit.sql
-- Phase 6 M5 (slip-service-integration) — 발행 감사 로그 테이블.
--
-- 회계 reference 영구 보존 — 매 출고전표 발행 1행씩 적재. soft-delete 만 적용 (실삭제 X).
--
-- 컬럼:
--   slip_id               — 발행된 Slip FK (logical, 도메인 격리 유지)
--   source_type/id/key    — Slip 의 동일 컬럼 snapshot
--   supply_amount         — legacy SaleList SUPPLY_AMT 합계 (라인 합계로 회계 검증용)
--   vat_amount            — legacy VAT_AMT 합계
--   applied_dc_snapshot   — DC/할인 정보 jsonb (legacy ADD_TXT_06_T 등 보존)
--   created_at/by         — BaseEntity 가 발행 시각/발행자 자동 기입 (감사 의미)

CREATE TABLE slip_publish_audit (
    id                    UUID            PRIMARY KEY,
    slip_id               UUID            NOT NULL,
    source_type           VARCHAR(32)     NOT NULL,
    source_id             VARCHAR(64),
    idempotency_key       VARCHAR(128),
    supply_amount         NUMERIC(17,2),
    vat_amount            NUMERIC(17,2),
    applied_dc_snapshot   JSONB,

    -- BaseEntity audit (plan §8)
    created_at            TIMESTAMP    NOT NULL,
    created_by            VARCHAR(50)  NOT NULL,
    modified_at           TIMESTAMP,
    modified_by           VARCHAR(50),
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(50),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_slip_publish_audit_slip
    ON slip_publish_audit (slip_id, is_deleted);

CREATE INDEX idx_slip_publish_audit_source
    ON slip_publish_audit (source_type, source_id, is_deleted);

CREATE INDEX idx_slip_publish_audit_idem
    ON slip_publish_audit (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
