-- V3__add_role_change_history.sql
-- Phase 10 P0-5 — admin 역할 변경 이력 테이블 (사용자 관리 화면 §4 변경 이력 탭).
-- BaseEntity audit columns 일관 (V1 / V2 와 동일).

CREATE TABLE role_change_history (
    id              UUID         PRIMARY KEY,
    employee_id     UUID         NOT NULL REFERENCES employees(id),
    previous_role   VARCHAR(20),
    new_role        VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),

    -- BaseEntity audit columns (plan §8)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_role_change_history_employee_active
    ON role_change_history (employee_id, created_at DESC)
    WHERE is_deleted = FALSE;
