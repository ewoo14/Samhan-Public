-- V5__add_slip_signature.sql (DevOps draft — BE 인용용)
-- Slip Service — Slice C (signature-slice-C Plan §3.1):
-- Slip 5필드 + share token 2필드 + slip_signature_audit 신규 테이블.
--
-- 컬럼 타입 컨벤션 (V1~V4 계승):
--   * 모든 신규 컬럼 nullable — ALTER ADD COLUMN nullable 은 PostgreSQL 11+ 메타데이터 only,
--     기존 row rewrite 없음 (대용량 테이블 안전)
--   * BYTEA 컬럼: 단일 컬럼 1GB 한계, 본 슬라이스 ≤50KB / row 안전
--   * partial UNIQUE INDEX (signature_share_token) — H2 PostgreSQL 모드 + PgSQL 16 모두 지원
--
-- H2/PgSQL 호환성:
--   * BYTEA       — H2 PostgreSQL 모드: BINARY/VARBINARY 로 매핑됨 (정상)
--   * VARCHAR(N)  — 양쪽 동일
--   * partial UNIQUE INDEX WHERE — H2 PostgreSQL 모드 (MODE=PostgreSQL) 지원
--   * UUID        — H2/PgSQL 동일

----------------------------------------------------------------------
-- 1) slips — Slice C 서명 7 컬럼 (Plan §3.1)
----------------------------------------------------------------------
ALTER TABLE slips ADD COLUMN signed_at                  TIMESTAMP;
ALTER TABLE slips ADD COLUMN signer_name                VARCHAR(50);
ALTER TABLE slips ADD COLUMN signature_png              BYTEA;
ALTER TABLE slips ADD COLUMN signature_hash             VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_channel          VARCHAR(20);
ALTER TABLE slips ADD COLUMN signature_share_token      VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_share_expires_at TIMESTAMP;

----------------------------------------------------------------------
-- 2) Partial UNIQUE INDEX — share token 충돌 방지 + lookup 가속
--    NULL 토큰 (서명 미완료 슬립) 은 인덱스 대상 제외 → 인덱스 사이즈 최소화
----------------------------------------------------------------------
CREATE UNIQUE INDEX uk_slip_signature_share_token
    ON slips (signature_share_token)
    WHERE signature_share_token IS NOT NULL;

----------------------------------------------------------------------
-- 3) Partial INDEX — 서명 완료 슬립 lookup 가속 (관리자 대시보드)
----------------------------------------------------------------------
CREATE INDEX ix_slips_signed_active
    ON slips (signed_at DESC)
    WHERE is_deleted = FALSE AND signed_at IS NOT NULL;

----------------------------------------------------------------------
-- 4) slip_signature_audit — 서명 이력 (RECORD / INVALIDATE) 감사 테이블
--    전자서명법 시행령 §17 무결성 입증 의무 — 서명 변경 이력 영구 보존
----------------------------------------------------------------------
CREATE TABLE slip_signature_audit (
    id                UUID         PRIMARY KEY,
    slip_id           UUID         NOT NULL,
    action            VARCHAR(20)  NOT NULL,  -- RECORD / INVALIDATE
    signer_name       VARCHAR(50),
    signature_hash    VARCHAR(64),
    reason            VARCHAR(500),
    actor_user_id     VARCHAR(50),            -- 공개 endpoint 시 NULL (PUBLIC_TOKEN)
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(50)  NOT NULL
);

CREATE INDEX ix_signature_audit_slip
    ON slip_signature_audit (slip_id, created_at DESC);

CREATE INDEX ix_signature_audit_action
    ON slip_signature_audit (action, created_at DESC);
