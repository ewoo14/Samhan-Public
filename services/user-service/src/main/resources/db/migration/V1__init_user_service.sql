-- V1__init_user_service.sql
-- User Service — initial schema for `departments` and `employees` (plan §3.4 first slice).
-- BaseEntity audit columns mirror auth-service.V1__init_account.sql exactly.
-- Soft-delete is enforced application-side via @SQLRestriction("is_deleted = false").
-- Note: PostgreSQL allows unquoted "position" in most contexts, but we use `job_title`
-- as the column name for clarity / forward-compatibility.

CREATE TABLE departments (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_departments_code_active
    ON departments (code)
    WHERE is_deleted = FALSE;

CREATE TABLE employees (
    id                UUID         PRIMARY KEY,
    account_id        UUID         NOT NULL,
    login_id          VARCHAR(50)  NOT NULL,
    full_name         VARCHAR(50)  NOT NULL,
    job_title         VARCHAR(30)  NOT NULL,
    role_snapshot     VARCHAR(20)  NOT NULL,
    department_id     UUID         NOT NULL REFERENCES departments(id),
    is_team_lead      BOOLEAN      NOT NULL DEFAULT FALSE,
    hire_date         DATE         NOT NULL,
    termination_date  DATE,
    email             VARCHAR(100),
    phone             VARCHAR(20),

    -- BaseEntity audit columns (plan §8)
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    modified_at       TIMESTAMP,
    modified_by       VARCHAR(50),
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(50),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_employees_account_id_active
    ON employees (account_id)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX ux_employees_login_id_active
    ON employees (login_id)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_employees_department_active
    ON employees (department_id, is_deleted);

CREATE INDEX ix_employees_role_active
    ON employees (role_snapshot, is_deleted);

-- At most one team lead per department among non-deleted employees.
CREATE UNIQUE INDEX ux_employees_one_lead_per_dept
    ON employees (department_id)
    WHERE is_team_lead = TRUE AND is_deleted = FALSE;
