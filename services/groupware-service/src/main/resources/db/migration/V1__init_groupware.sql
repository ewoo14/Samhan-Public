-- V1__init_groupware.sql
-- Phase 9 W2 groupware-service — initial schema (3 entity + 2 부속).
-- BaseEntity audit columns mirror partner-service.V1 정확히.
-- Soft-delete 는 application-side @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 VARCHAR(N) (CHAR/bpchar 금지)
--   * 본문 / 메모 등 가변 큰 텍스트는 VARCHAR(2000) — 본 슬라이스 도메인 표준
--   * 시간은 TIMESTAMP (Hibernate JPA LocalDateTime 매핑)

----------------------------------------------------------------------
-- 1) approval_lines — 결재선 (1건 = 요청 1건).
--    requester_id   = 요청자 user UUID
--    status         = PENDING / IN_PROGRESS / APPROVED / REJECTED / WITHDRAWN
----------------------------------------------------------------------
CREATE TABLE approval_lines (
    id              UUID         PRIMARY KEY,
    requester_id    UUID         NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         VARCHAR(2000),
    status          VARCHAR(20)  NOT NULL,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 요청자별 inbox + 상태 필터 검색 인덱스 (활성 행 한정)
CREATE INDEX ix_approval_lines_requester_status_active
    ON approval_lines (requester_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_approval_lines_status_active
    ON approval_lines (status)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) approval_steps — 결재 chain 단계 (1 line : N step, sequence ASC).
--    sequence       = 0-base ordering
--    status         = PENDING / APPROVED / REJECTED
----------------------------------------------------------------------
CREATE TABLE approval_steps (
    id                  UUID         PRIMARY KEY,
    approval_line_id    UUID         NOT NULL REFERENCES approval_lines(id),
    approver_id         UUID         NOT NULL,
    sequence            INT          NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    decided_at          TIMESTAMP,
    reason              VARCHAR(500),

    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

-- chain 활성 행 한정 — line + sequence 조합 unique
CREATE UNIQUE INDEX ux_approval_steps_line_sequence_active
    ON approval_steps (approval_line_id, sequence)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_approval_steps_approver_status_active
    ON approval_steps (approver_id, status)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 3) messages — 메신저 (1:1 row 단위).
--    status   = UNREAD / READ
----------------------------------------------------------------------
CREATE TABLE messages (
    id              UUID         PRIMARY KEY,
    sender_id       UUID         NOT NULL,
    recipient_id    UUID         NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    sent_at         TIMESTAMP    NOT NULL,
    read_at         TIMESTAMP,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 수신자 inbox + 미열람 카운트 인덱스 (활성 행 한정)
CREATE INDEX ix_messages_recipient_sent_active
    ON messages (recipient_id, sent_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_messages_recipient_status_active
    ON messages (recipient_id, status)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 4) schedules — 일정 1건.
--    status   = DRAFT / CONFIRMED / CANCELLED
----------------------------------------------------------------------
CREATE TABLE schedules (
    id              UUID         PRIMARY KEY,
    owner_id        UUID         NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    starts_at       TIMESTAMP    NOT NULL,
    ends_at         TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_schedules_owner_starts_active
    ON schedules (owner_id, starts_at)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_schedules_status_active
    ON schedules (status)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 5) schedule_participants — 일정 참여자 (1 schedule : N).
----------------------------------------------------------------------
CREATE TABLE schedule_participants (
    id              UUID         PRIMARY KEY,
    schedule_id     UUID         NOT NULL REFERENCES schedules(id),
    participant_id  UUID         NOT NULL,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 활성 행 schedule + participant 조합 unique (중복 추가 방지)
CREATE UNIQUE INDEX ux_schedule_participants_unique_active
    ON schedule_participants (schedule_id, participant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_schedule_participants_participant_active
    ON schedule_participants (participant_id)
    WHERE is_deleted = FALSE;
