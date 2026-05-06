-- V1__init_notification.sql
-- Phase 9 W3 notification-service — initial schema (2 entity).
-- BaseEntity audit columns mirror partner-service / groupware-service V1 정확히.
-- Soft-delete 는 application-side @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 VARCHAR(N) (CHAR/bpchar 금지)
--   * 외부 발송 payload 는 JSONB (Postgres standard, RDS 호환)
--   * 시간은 TIMESTAMP (Hibernate JPA LocalDateTime 매핑)

----------------------------------------------------------------------
-- 1) notification_requests — 발송 요청 1건.
--    recipient_type   = USER / PARTNER / EXTERNAL_PHONE
--    recipient_id     = USER/PARTNER 인 경우 user-service / partner-service 의 UUID
--    recipient_address= EXTERNAL_PHONE 또는 보조 채널 주소 (이메일 / 전화)
--    channel          = PUSH / EMAIL / SMS
--    template_code    = (선택) 사전 등록 템플릿 코드
--    payload          = JSONB (변수 치환 / 추가 메타)
--    status           = PENDING / SENT / FAILED / RETRYING
----------------------------------------------------------------------
CREATE TABLE notification_requests (
    id                  UUID         PRIMARY KEY,
    recipient_type      VARCHAR(20)  NOT NULL,
    recipient_id        UUID,
    recipient_address   VARCHAR(200),
    channel             VARCHAR(20)  NOT NULL,
    template_code       VARCHAR(50),
    subject             VARCHAR(200),
    body                VARCHAR(2000),
    payload             JSONB,
    status              VARCHAR(20)  NOT NULL,
    last_attempted_at   TIMESTAMP,
    attempt_count       INT          NOT NULL DEFAULT 0,

    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 채널별 상태 검색 — 운영 dashboard / 재시도 batch 용 (활성 행 한정)
CREATE INDEX ix_notification_requests_channel_status_active
    ON notification_requests (channel, status)
    WHERE is_deleted = FALSE;

-- 수신자별 inbox / 사용자 본인 발송 이력 검색
CREATE INDEX ix_notification_requests_recipient_active
    ON notification_requests (recipient_id)
    WHERE is_deleted = FALSE;

-- 상태별 batch — 실패 / 재시도 큐 처리
CREATE INDEX ix_notification_requests_status_active
    ON notification_requests (status)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) notification_logs — 발송 이력 (1 request : N attempt).
--    attempt_no       = 1-base, 재시도 시 증가
--    gateway_status   = 게이트웨이 응답 코드 (SUCCESS / FAILURE_<code>)
--    gateway_response = raw response 페이로드 (디버깅 용, 길이 무제한)
--    sent_at          = 게이트웨이 호출 완료 시각
----------------------------------------------------------------------
CREATE TABLE notification_logs (
    id                  UUID         PRIMARY KEY,
    request_id          UUID         NOT NULL REFERENCES notification_requests(id),
    channel             VARCHAR(20)  NOT NULL,
    attempt_no          INT          NOT NULL,
    gateway_status      VARCHAR(50)  NOT NULL,
    gateway_message_id  VARCHAR(200),
    gateway_response    TEXT,
    sent_at             TIMESTAMP    NOT NULL,

    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 요청별 attempt 시퀀스 unique (활성 행 한정)
CREATE UNIQUE INDEX ux_notification_logs_request_attempt_active
    ON notification_logs (request_id, attempt_no)
    WHERE is_deleted = FALSE;

-- request 단건 lookup 가속 (admin 조회 — 발송 이력 view)
CREATE INDEX ix_notification_logs_request_active
    ON notification_logs (request_id, sent_at DESC)
    WHERE is_deleted = FALSE;
