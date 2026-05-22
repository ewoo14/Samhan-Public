-- V5__create_notification.sql
-- Issue 4 통합 알림 센터 — Slice 1 (2026-05-22)
-- 사용자 알림 (multi-channel) 단일 entity. NotificationLog (게이트웨이 발송 이력) 와 별개.

CREATE TABLE notification_center (
    id               UUID         PRIMARY KEY,
    channel          VARCHAR(32)  NOT NULL,
    severity         VARCHAR(16)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    body             TEXT,
    target_role      VARCHAR(200),
    target_user_id   UUID,
    source_service   VARCHAR(64)  NOT NULL,
    source_ref_id    VARCHAR(200),
    deeplink         VARCHAR(500),
    read_at          TIMESTAMP,

    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    modified_at      TIMESTAMP,
    modified_by      VARCHAR(50),
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(50),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notification_center_target_role_unread
    ON notification_center(target_role, read_at)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_notification_center_target_user_unread
    ON notification_center(target_user_id, read_at)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_notification_center_source_ref
    ON notification_center(source_service, source_ref_id, channel);

CREATE INDEX idx_notification_center_created_at
    ON notification_center(created_at DESC)
    WHERE is_deleted = FALSE;
