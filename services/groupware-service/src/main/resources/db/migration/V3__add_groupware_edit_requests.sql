-- V3__add_groupware_edit_requests.sql
-- Groupware Service — PR-H4b (Phase 12 Step 4b) BE-E: shared:realtime-abstraction 적용.
--
-- 컨텍스트:
--   * 본 단계 (PR-H4b BE-E) = groupware-service 에 edit_request 테이블 적용. 게시 후 변경 신중 정책
--     (slip-service PR-H3 의 잠금 패턴 일관) — ApprovalLine 결재 진행 중 / Message 발신 후 / Schedule
--     공유 후 변경 시 권한자 (MANAGER 등) 수락 후 mutation 가능하게 하는 워크플로우 채널.
--   * 본 PR 범위는 schema + entity + repository 까지 (실 mutation 가드 통합은 향후 PR).
--
-- 스키마 = shared:realtime-abstraction `db/template/edit_request_template.sql` 일관.
-- 컬럼명 entity_id 유지 (EditRequestRecord @MappedSuperclass 와 매핑).
--
-- 컬럼 컨벤션 (BaseEntity 7 + EditRequestRecord 13):
--   * id UUID PK
--   * entity_id UUID NOT NULL — 소속 도메인 entity FK (FK 미강제)
--   * requester_id UUID NOT NULL — 요청자 UUID
--   * requester_name VARCHAR(50) NOT NULL — 요청자 표시명 (UUID 비공개 가드)
--   * request_type VARCHAR(20) NOT NULL — EDIT | DELETE
--   * reason VARCHAR(500) — 요청 사유 (선택)
--   * status VARCHAR(20) NOT NULL — PENDING | APPROVED | REJECTED | EXPIRED
--   * target_role VARCHAR(20) NOT NULL — WAREHOUSE | MANAGER (수락 권한자 그룹)
--   * decided_by_id UUID — 결정자 UUID
--   * decided_by_name VARCHAR(50) — 결정자 표시명 (UUID 비공개 가드)
--   * decided_at TIMESTAMP — 결정 시각
--   * decision_reason VARCHAR(500) — 거절 사유 (REJECTED 필수)
--   * requested_at TIMESTAMP NOT NULL — 요청 시각
--   * expires_at TIMESTAMP — 자동 만료 시각
--   * BaseEntity 7: created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted

CREATE TABLE groupware_edit_requests (
    id                  UUID         PRIMARY KEY,

    -- EditRequestRecord 13 필드
    entity_id           UUID         NOT NULL,
    requester_id        UUID         NOT NULL,
    requester_name      VARCHAR(50)  NOT NULL,
    request_type        VARCHAR(20)  NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    target_role         VARCHAR(20)  NOT NULL,
    decided_by_id       UUID,
    decided_by_name     VARCHAR(50),
    decided_at          TIMESTAMP,
    decision_reason     VARCHAR(500),
    requested_at        TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP,

    -- BaseEntity 7 audit 필드
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE groupware_edit_requests IS
    'PR-H4b BE-E groupware 도메인 entity 게시 후 변경 요청 워크플로우 (slip-service PR-H3 잠금 패턴 일관)';

COMMENT ON COLUMN groupware_edit_requests.entity_id IS
    '소속 도메인 entity FK (ApprovalLine.id / Message.id / Schedule.id 등). FK 미강제';

COMMENT ON COLUMN groupware_edit_requests.requester_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자';

COMMENT ON COLUMN groupware_edit_requests.decided_by_name IS
    'UUID 비공개 가드 — 결정자 표시명';

-- entity 별 활성 요청 조회 (mutation 가드)
CREATE INDEX ix_groupware_edit_requests_entity_status
    ON groupware_edit_requests (entity_id, status)
    WHERE is_deleted = FALSE;

-- 권한자 그룹 대시보드 (MANAGER PENDING 목록)
CREATE INDEX ix_groupware_edit_requests_role_status
    ON groupware_edit_requests (target_role, status, requested_at DESC)
    WHERE is_deleted = FALSE;

-- 스케줄러 자동 만료 (PENDING + expires_at < now)
CREATE INDEX ix_groupware_edit_requests_pending_expires
    ON groupware_edit_requests (status, expires_at)
    WHERE is_deleted = FALSE AND status = 'PENDING' AND expires_at IS NOT NULL;
