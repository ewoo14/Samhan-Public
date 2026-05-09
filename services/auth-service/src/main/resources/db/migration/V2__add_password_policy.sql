-- V2__add_password_policy.sql
-- Phase 10 P0-2 (manual 06-트러블슈팅/01-로그인-실패.md §1-3) — 비밀번호 재설정 + 정책 + 잠금 컬럼.
--
-- 1) failed_login_attempts  : 5회 실패 시 잠금 카운터 (login 성공 시 0 reset)
-- 2) locked_at              : 잠금 시점 (NULL = 정상). MASTER unlock 시 NULL 로 복구
-- 3) password_changed_at    : 비밀번호 마지막 변경 시점 (JWT 무효 비교용 — 기존 token 의 iat < password_changed_at 이면 거절)
-- 4) password_history       : 최근 5개 BCrypt hash JSONB 배열 (reuse 금지). 신규 row = '[]'
-- 5) password_reset_token   : UUID4 단일 사용 토큰 (request 시 발급, confirm 후 NULL 처리)
-- 6) password_reset_token_expires_at : 토큰 만료 (30분). 만료 시 토큰 무효
--
-- 모든 컬럼은 nullable 또는 default 보유 — 기존 row 대상 backfill 불필요.
ALTER TABLE accounts
    ADD COLUMN failed_login_attempts        INT          NOT NULL DEFAULT 0,
    ADD COLUMN locked_at                    TIMESTAMP    NULL,
    ADD COLUMN password_changed_at          TIMESTAMP    NULL,
    ADD COLUMN password_history             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN password_reset_token         VARCHAR(255) NULL,
    ADD COLUMN password_reset_token_expires_at TIMESTAMP NULL;

-- 토큰으로 빠른 조회 (request → confirm 흐름의 핵심 lookup). 부분 unique index 로 동시 활성 토큰 1건 보장.
CREATE UNIQUE INDEX ux_accounts_password_reset_token_active
    ON accounts (password_reset_token)
    WHERE password_reset_token IS NOT NULL AND is_deleted = FALSE;

-- 잠긴 계정 운영 조회 (MASTER 화면)
CREATE INDEX ix_accounts_locked_at
    ON accounts (locked_at)
    WHERE locked_at IS NOT NULL AND is_deleted = FALSE;
