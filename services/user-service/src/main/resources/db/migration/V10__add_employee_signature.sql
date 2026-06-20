-- V10__add_employee_signature.sql
-- C1a - 사원 서명(인감) 저장소: employees 4컬럼 + 감사 테이블.
--
-- slip-service V5__add_slip_signature.sql 패턴 미러. 단 user 도메인 채널은
-- {MOBILE_CANVAS, UPLOAD} 2종 (slip 의 PAPER_SCAN 미사용). 핸드오프 토큰 테이블은 C1b(V11).
--
-- 컬럼 타입 컨벤션 (V1/V8 계승):
--   * 모든 신규 컬럼 nullable (미등록 = NULL)
--   * BYTEA: PNG 50KB 이하 (서비스 레이어 가드)
--   * SHA-256 hex 64자 -> VARCHAR(64)
--   * enum -> VARCHAR(20) + CHECK (도메인 enum / FE 타입 3곳 정확 일치)

----------------------------------------------------------------------
-- 1) employees - 서명 4컬럼 추가 + 채널 CHECK 제약
----------------------------------------------------------------------
ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_png       BYTEA;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_hash      VARCHAR(64);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS signed_at           TIMESTAMP;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_channel   VARCHAR(20);

ALTER TABLE employees
    ADD CONSTRAINT ck_employees_signature_channel
    CHECK (signature_channel IS NULL
           OR signature_channel IN ('MOBILE_CANVAS', 'UPLOAD'));

----------------------------------------------------------------------
-- 2) employee_signature_audit - 등록/무효화 이력 (slip_signature_audit 미러)
----------------------------------------------------------------------
CREATE TABLE employee_signature_audit (
    id                 UUID         PRIMARY KEY,
    employee_id        UUID         NOT NULL,
    action             VARCHAR(20)  NOT NULL,
    signature_hash     VARCHAR(64),
    signature_channel  VARCHAR(20),
    reason             VARCHAR(500),
    actor_user_id      VARCHAR(50),

    -- BaseEntity audit (V1/V3 컨벤션 그대로)
    created_at         TIMESTAMP    NOT NULL,
    created_by         VARCHAR(50)  NOT NULL,
    modified_at        TIMESTAMP,
    modified_by        VARCHAR(50),
    deleted_at         TIMESTAMP,
    deleted_by         VARCHAR(50),
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_employee_signature_audit_action
        CHECK (action IN ('RECORD', 'INVALIDATE')),
    CONSTRAINT ck_employee_signature_audit_channel
        CHECK (signature_channel IS NULL
               OR signature_channel IN ('MOBILE_CANVAS', 'UPLOAD'))
);

CREATE INDEX ix_employee_signature_audit_employee
    ON employee_signature_audit (employee_id, created_at DESC);

CREATE INDEX ix_employee_signature_audit_action
    ON employee_signature_audit (action, created_at DESC);
