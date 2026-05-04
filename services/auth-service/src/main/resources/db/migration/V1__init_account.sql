-- V1__init_account.sql
-- Auth Service — initial schema for the `accounts` table (plan §3.4 / §8 BaseEntity).
-- All seven BaseEntity audit columns are declared inline; soft-delete is enforced
-- application-side via @SQLRestriction("is_deleted = false") on the entity.

CREATE TABLE accounts (
    id              UUID         PRIMARY KEY,
    login_id        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- login_id must be unique only among non-deleted rows (allow re-use after soft-delete).
CREATE UNIQUE INDEX ux_accounts_login_id_active
    ON accounts (login_id)
    WHERE is_deleted = FALSE;

-- Common admin filter: list all active users by role.
CREATE INDEX ix_accounts_role_active
    ON accounts (role, is_deleted);
