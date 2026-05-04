-- V5__add_slip_signature.sql
-- Slip Service — Slice C (signature-slice-C Plan §3.1):
-- 모바일 인수자 전자서명 (Canvas PNG + SHA-256 + share token + 30일 만료)
--
-- 도메인 모델 C안 (Plan §1.1) — Slip 1:1 5필드 + 별도 audit 이력 테이블.
-- 라이프사이클 (Plan §1.3 Layer 4):
--   recordSignature(signerName, png, hash) — INSPECTING/COMPLETED/SHIPPING 단계만 허용
--   invalidateSignature(reason, by)        — signedAt!=null 일 때만, 5필드 NULL + audit
--
-- 컬럼 타입 컨벤션 (V1 계승):
--   * 짧은 문자열 VARCHAR(N)
--   * 모든 신규 컬럼 nullable — 기존 슬립 호환
--   * BYTEA: PNG 바이너리 ≤50KB (서비스 레이어 가드)
--   * SHA-256 hex 64자 → VARCHAR(64)

----------------------------------------------------------------------
-- 1) slips — 서명 관련 7 필드 신규 추가 (Plan §1.1 + share token 만료 컬럼 분리)
----------------------------------------------------------------------
ALTER TABLE slips ADD COLUMN signed_at                  TIMESTAMP;
ALTER TABLE slips ADD COLUMN signer_name                VARCHAR(50);
ALTER TABLE slips ADD COLUMN signature_png              BYTEA;
ALTER TABLE slips ADD COLUMN signature_hash             VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_channel          VARCHAR(20);
ALTER TABLE slips ADD COLUMN signature_share_token      VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_share_expires_at TIMESTAMP;

-- partial UNIQUE INDEX — token 발급된 슬립만 유일성 강제 (NULL 허용)
CREATE UNIQUE INDEX uk_slip_signature_share_token
    ON slips (signature_share_token)
    WHERE signature_share_token IS NOT NULL;

-- 서명 완료 슬립 lookup 가속화 (admin 화면 필터)
CREATE INDEX ix_slips_signed_active
    ON slips (signed_at DESC)
    WHERE is_deleted = FALSE AND signed_at IS NOT NULL;

----------------------------------------------------------------------
-- 2) slip_signature_audit — 서명/무효화 이력 (Plan §3.1)
--    전자서명법 시행령 §17 무결성 입증 의무 — 별도 테이블 필수.
----------------------------------------------------------------------
CREATE TABLE slip_signature_audit (
    id              UUID         PRIMARY KEY,
    slip_id         UUID         NOT NULL,
    action          VARCHAR(20)  NOT NULL,  -- RECORD / INVALIDATE
    signer_name     VARCHAR(50),
    signature_hash  VARCHAR(64),
    reason          VARCHAR(500),
    actor_user_id   VARCHAR(50),            -- 공개 endpoint(SIGN) 시 NULL

    -- BaseEntity audit (V1 컨벤션 그대로)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_signature_audit_slip
    ON slip_signature_audit (slip_id, created_at DESC);

CREATE INDEX ix_signature_audit_action
    ON slip_signature_audit (action, created_at DESC);
