-- V19__add_slip_edit_requests.sql
-- Slip Service — PR-H3 (Phase 12 Step 3): 슬립 수정/삭제 요청 워크플로우.
--
-- 컨텍스트:
--   * Phase 12 Step 1 (PR-H1, V17) = SSE infra + slip_comments smoke 도메인.
--   * Phase 12 Step 2 (PR-H2, V18) = audit overlay + revision (slip_audit_logs).
--   * 본 단계 (PR-H3) = "슬립 잠금 정책 + 수정/삭제 요청 워크플로우".
--     사용자 명시 잠금 정책 (개발책임자 결정):
--       - DRAFT/SAVED/SENT  : 작성자 자유 수정/삭제
--       - CONFIRMED (창고 인계 = ACCEPTED 이후) : 수정/삭제 차단 → 알림 요청 →
--         창고 직원 (ROLE_WAREHOUSE) 또는 관리자 (ROLE_MANAGER) 수락 시 가능
--       - INSPECTING + SHIPPING : 창고도 수락 불가 (완전 잠금 — picking 진행 중)
--       - DELIVERED + CONFIRMED : 영구 잠금 (회계 마감 직전/이후)
--   * Samhan Public 이식 강조 — 외부 vendor 의존은 notification-service Aligo SMS 만.
--
-- 컬럼 컨벤션 (BaseEntity 7 audit + Soft Delete):
--   * id UUID PK
--   * slip_id UUID NOT NULL — FK 미강제 (slip soft delete 와 분리, audit 영구 보존)
--   * requester_id UUID NOT NULL — 요청자 UUID (audit/감사 추적용)
--   * requester_name VARCHAR(50) NOT NULL — 요청자 표시명 (UUID 비공개 가드)
--   * request_type VARCHAR(20) NOT NULL — EDIT | DELETE
--   * reason VARCHAR(500) — 요청 사유 (선택)
--   * status VARCHAR(20) NOT NULL DEFAULT 'PENDING' — PENDING | APPROVED | REJECTED | EXPIRED
--   * target_role VARCHAR(20) NOT NULL — WAREHOUSE | MANAGER (수락 권한자 그룹)
--   * decided_by_id UUID — 결정자 UUID (수락/거절 시점 채움)
--   * decided_by_name VARCHAR(50) — 결정자 표시명 (UUID 비공개 가드)
--   * decided_at TIMESTAMP — 결정 시각
--   * decision_reason VARCHAR(500) — 거절 사유 (REJECTED 시점 필수, APPROVED 시 선택)
--   * requested_at TIMESTAMP NOT NULL — 요청 시각
--   * expires_at TIMESTAMP — 자동 만료 시각 (default 24h, app.slip.edit-request.expires-hours)
--   * BaseEntity 7: created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 slip / slip_audit_logs / slip_comments IT 영향 0
--   * FK 미강제 — slip soft delete 후에도 요청 row 보존 (감사)

CREATE TABLE slip_edit_requests (
    id              UUID         PRIMARY KEY,
    slip_id         UUID         NOT NULL,
    requester_id    UUID         NOT NULL,
    requester_name  VARCHAR(50)  NOT NULL,
    request_type    VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    target_role     VARCHAR(20)  NOT NULL,
    decided_by_id   UUID,
    decided_by_name VARCHAR(50),
    decided_at      TIMESTAMP,
    decision_reason VARCHAR(500),
    requested_at    TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE slip_edit_requests IS
    'PR-H3 슬립 수정/삭제 요청 워크플로우 — Phase 12 Step 3. CONFIRMED 슬립의 mutation 잠금 해제 채널';

COMMENT ON COLUMN slip_edit_requests.request_type IS
    'EDIT (헤더/라인/필드 수정 요청) | DELETE (슬립 자체 삭제 요청)';

COMMENT ON COLUMN slip_edit_requests.status IS
    'PENDING (요청 직후) → APPROVED (수락, slip mutation 1회 가능) | REJECTED (거절) | EXPIRED (자동 만료)';

COMMENT ON COLUMN slip_edit_requests.target_role IS
    'WAREHOUSE (창고 직원 그룹) | MANAGER (관리자 그룹). 사용자 명시 정책 — INSPECTING/SHIPPING/DELIVERED 단계는 수락 불가';

COMMENT ON COLUMN slip_edit_requests.requester_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. requester_id (UUID) 와 분리';

COMMENT ON COLUMN slip_edit_requests.decided_by_name IS
    'UUID 비공개 가드 — 결정자 표시명. decided_by_id 와 분리';

COMMENT ON COLUMN slip_edit_requests.expires_at IS
    '자동 만료 시각 — app.slip.edit-request.expires-hours (default 24h). 스케줄러가 PENDING + expires_at < now 인 row 를 EXPIRED 전환';

-- 슬립별 활성 요청 조회 (mutation 가드 — APPROVED 1건 있어야 수정/삭제 진행)
CREATE INDEX ix_slip_edit_requests_slip_status
    ON slip_edit_requests (slip_id, status)
    WHERE is_deleted = FALSE;

-- 권한자 그룹 대시보드 (창고 직원 / 관리자 PENDING 목록)
CREATE INDEX ix_slip_edit_requests_target_role
    ON slip_edit_requests (target_role, status)
    WHERE is_deleted = FALSE;

-- PENDING + expires_at 인덱스 (스케줄러 만료 처리 효율 — 수십만 row 환경 회귀 가드)
CREATE INDEX ix_slip_edit_requests_pending_expires
    ON slip_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING';
