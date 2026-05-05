-- V1__init_partner_auth.sql
-- Phase 6 M2 — partner-auth-service initial schema (설계서 §4).
-- 3 entity: partner_auth + partner_login_attempt + partner_session.
-- BaseEntity 7 audit columns inline; soft-delete 는 entity 의 @SQLRestriction 으로 적용.

-- ─────────────────────────────────────────────────────────────────────
-- 1) partner_auth — 파트너 인증 정보 1건 (bizNo UNIQUE)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE partner_auth (
    id                      UUID         PRIMARY KEY,
    biz_no                  VARCHAR(12)  NOT NULL,
    partner_code            VARCHAR(30),
    password_hash           VARCHAR(200),
    password_history        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status                  VARCHAR(30)  NOT NULL,
    failed_attempts         INTEGER      NOT NULL DEFAULT 0,
    last_login_at           TIMESTAMP,
    password_changed_at     TIMESTAMP,
    tutorial_pc_done        BOOLEAN      NOT NULL DEFAULT FALSE,
    tutorial_mobile_done    BOOLEAN      NOT NULL DEFAULT FALSE,
    register_memo           VARCHAR(500),

    -- BaseEntity audit columns (plan §8)
    created_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(50)  NOT NULL,
    modified_at             TIMESTAMP,
    modified_by             VARCHAR(50),
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(50),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE
);

-- bizNo 는 active row 안에서만 unique (soft-delete 후 재가입 허용).
CREATE UNIQUE INDEX ux_partner_auth_biz_no_active
    ON partner_auth (biz_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_auth_status_active
    ON partner_auth (status, is_deleted);

CREATE INDEX ix_partner_auth_partner_code
    ON partner_auth (partner_code)
    WHERE is_deleted = FALSE;

-- ─────────────────────────────────────────────────────────────────────
-- 2) partner_login_attempt — 로그인 시도 audit
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE partner_login_attempt (
    id                      UUID         PRIMARY KEY,
    auth_id                 UUID,
    biz_no                  VARCHAR(12)  NOT NULL,
    result                  VARCHAR(30)  NOT NULL,
    client_ip               VARCHAR(45),
    user_agent              VARCHAR(500),
    is_mobile               BOOLEAN      NOT NULL DEFAULT FALSE,
    attempted_at            TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(50)  NOT NULL,
    modified_at             TIMESTAMP,
    modified_by             VARCHAR(50),
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(50),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_partner_login_attempt_biz_no_attempted_at
    ON partner_login_attempt (biz_no, attempted_at DESC);

CREATE INDEX ix_partner_login_attempt_auth_id
    ON partner_login_attempt (auth_id);

CREATE INDEX ix_partner_login_attempt_result
    ON partner_login_attempt (result);

-- ─────────────────────────────────────────────────────────────────────
-- 3) partner_session — JWT JTI + 만료/취소
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE partner_session (
    id                      UUID         PRIMARY KEY,
    jti                     VARCHAR(64)  NOT NULL,
    auth_id                 UUID         NOT NULL,
    biz_no                  VARCHAR(12)  NOT NULL,
    issued_at               TIMESTAMP    NOT NULL,
    expires_at              TIMESTAMP    NOT NULL,
    revoked_at              TIMESTAMP,
    client_ip               VARCHAR(45),

    created_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(50)  NOT NULL,
    modified_at             TIMESTAMP,
    modified_by             VARCHAR(50),
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(50),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_partner_session_jti
    ON partner_session (jti)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_partner_session_auth_id
    ON partner_session (auth_id);

CREATE INDEX ix_partner_session_expires_at
    ON partner_session (expires_at);
