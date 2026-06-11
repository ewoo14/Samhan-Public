-- V37__add_dispatch_collab_comments.sql
-- DispatchTask 협업 댓글 — shared/collab-core CollabCommentService 첫 실배선.
--
-- 컬럼 컨벤션: V21__add_dispatch_task_tables.sql 과 동일
-- (TIMESTAMP, modified_at/by, deleted_by, is_deleted BOOLEAN DEFAULT FALSE).
-- arologis 전송/수정 요청 흐름과 분리된 순수 댓글 채널이다.

CREATE TABLE dispatch_collab_comments (
    id              UUID         PRIMARY KEY,
    document_type   VARCHAR(40)  NOT NULL
                    CHECK (document_type IN (
                        'DISPATCH_TASK',
                        'SLIP_OUTBOUND',
                        'SLIP_INBOUND',
                        'ACCOUNTING_VOUCHER',
                        'PARTNER_ORDER',
                        'ESTIMATE')),
    document_id     UUID         NOT NULL,
    anchor          VARCHAR(120),
    author_id       UUID         NOT NULL,
    author_name     VARCHAR(50)  NOT NULL,
    body            VARCHAR(500) NOT NULL,
    parent_id       UUID,
    status          VARCHAR(20)  NOT NULL
                    CHECK (status IN ('OPEN','RESOLVED')),

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE dispatch_collab_comments IS
    'DispatchTask 협업 댓글 — shared/collab-core CollabCommentService 배차 reference';

COMMENT ON COLUMN dispatch_collab_comments.author_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. author_id UUID 와 분리';

CREATE INDEX ix_dispatch_collab_comments_document_timeline
    ON dispatch_collab_comments (document_id, created_at);

CREATE INDEX ix_dispatch_collab_comments_document_active
    ON dispatch_collab_comments (document_id)
    WHERE is_deleted = FALSE;
