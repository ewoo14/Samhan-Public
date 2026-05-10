-- V17__add_slip_comments.sql
-- Slip Service — PR-H1 (Phase 12 Step 1): SSE 실시간 인프라 + 슬립 댓글 smoke 도메인.
--
-- 컨텍스트:
--   * Phase 12 = 실시간 협업 (sales 직원 ↔ 창고원 ↔ 검수자) — SseEmitter (Spring 표준) 기반
--     in-memory broker 로 단일 노드 신호 전송. 외부 SaaS (Pusher/Ably/PubNub) 의존 0.
--   * smoke 도메인 = slip_comments — 슬립 1건당 N 댓글, 최근 20건 백필 + SSE push 신규 댓글.
--   * Samhan Public 이식 강조 — 자체 Spring SseEmitter infra 만 사용.
--
-- 컬럼 컨벤션 (BaseEntity 7 audit + Soft Delete):
--   * id UUID PK, slip_id UUID NOT NULL (FK 미강제 — slip 삭제와 분리, soft delete 일관)
--   * author_id UUID NOT NULL (gateway X-User-Id 또는 mobile-staff token sub)
--   * author_name VARCHAR(50) NOT NULL — UUID 비공개 가드, 사용자 노출 식별자
--   * body VARCHAR(500) NOT NULL — 단순 텍스트 메모 (이미지/파일은 slip_attachments 별도)
--   * BaseEntity 7: created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 slip / slip_lines / slip_attachments IT 영향 0
--   * FK 미설정 — slip soft delete 후에도 댓글 row 보존 (감사 추적)

CREATE TABLE slip_comments (
    id              UUID         PRIMARY KEY,
    slip_id         UUID         NOT NULL,
    author_id       UUID         NOT NULL,
    author_name     VARCHAR(50)  NOT NULL,
    body            VARCHAR(500) NOT NULL,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE slip_comments IS
    'PR-H1 슬립 댓글 — Phase 12 SSE realtime smoke. 슬립 1건당 N 댓글, 최근 20건 백필 + 신규 SSE push';

COMMENT ON COLUMN slip_comments.author_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. author_id (UUID) 와 분리';

COMMENT ON COLUMN slip_comments.body IS
    '단순 텍스트 메모 (≤500자). 이미지/파일은 slip_attachments 별도 첨부';

-- 슬립별 활성 댓글 조회용 부분 인덱스 (soft-deleted 제외)
CREATE INDEX ix_slip_comments_slip_id
    ON slip_comments (slip_id, created_at DESC)
    WHERE is_deleted = FALSE;
