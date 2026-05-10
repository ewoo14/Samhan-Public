-- V18__add_slip_audit_logs.sql
-- Slip Service — PR-H2 (Phase 12 Step 2): 슬립 본문 audit overlay + 실시간 sync.
--
-- 컨텍스트:
--   * Phase 12 Step 1 (PR-H1, V17) = SSE infra + slip_comments smoke 도메인.
--   * 본 단계 = "슬립 본문 수정 = 자동 audit 저장 + 실시간 broadcast" 시범 적용 (slip-service 한정).
--     audit overlay 의 모든 영역 확장 (estimate/order/partner-master 등) 은 PR-H4 일괄 처리 예정.
--   * Samhan Public 이식 강조 — 외부 vendor 의존 0 (audit 자체는 PostgreSQL row, 실시간 sync 는
--     PR-H1 SlipRealtimeBroker 재사용).
--
-- 컬럼 컨벤션 (BaseEntity 7 audit + Soft Delete):
--   * id UUID PK
--   * slip_id UUID NOT NULL — FK 미강제 (slip soft delete 와 분리, audit 영구 보존)
--   * revision_no INT NOT NULL — 1, 2, 3, ... 슬립별 단조 증가 수정 횟수 (slips.revision_count 와 일관)
--   * actor_id UUID NOT NULL — 수정자 UUID (audit/감사 추적용, 사용자 화면 노출 금지)
--   * actor_name VARCHAR(50) NOT NULL — 수정자 표시명 (UUID 비공개 가드)
--   * actor_color VARCHAR(20) — HSL hex (FE userIdToColor 결과 backup, optional)
--   * field_name VARCHAR(50) NOT NULL — 'memo' / 'shippingAddress' / 'lines[0].quantity' 등
--   * old_value TEXT — 이전 값 (취소선 표시용, NULL 허용 = 신규 라인/필드)
--   * new_value TEXT — 새 값 (NULL 허용 = 라인 삭제)
--   * changed_at TIMESTAMP NOT NULL — 변경 시각 (BaseEntity.createdAt 과 동일하지만 명시 보존
--                                     — revert 시 정렬 정확성 보장)
--   * BaseEntity 7: created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted
--
-- 회귀 영향:
--   * 신규 테이블 — 기존 slip / slip_lines / slip_comments IT 영향 0
--   * slips.revision_count 신규 컬럼 — DEFAULT 0, 기존 row 자동 backfill (NOT NULL 안전)
--   * FK 미강제 — slip soft delete 후에도 audit log row 보존 (회계 감사)

CREATE TABLE slip_audit_logs (
    id              UUID         PRIMARY KEY,
    slip_id         UUID         NOT NULL,
    revision_no     INT          NOT NULL,
    actor_id        UUID         NOT NULL,
    actor_name      VARCHAR(50)  NOT NULL,
    actor_color     VARCHAR(20),
    field_name      VARCHAR(50)  NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_at      TIMESTAMP    NOT NULL,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE slip_audit_logs IS
    'PR-H2 슬립 본문 수정 audit overlay — Phase 12 Step 2. 필드별 diff 1행 기록 + SSE broadcast';

COMMENT ON COLUMN slip_audit_logs.revision_no IS
    '슬립별 단조 증가 수정 횟수 (slips.revision_count 와 동기화). 같은 트랜잭션의 다중 필드 변경은 같은 revision_no 공유';

COMMENT ON COLUMN slip_audit_logs.actor_name IS
    'UUID 비공개 가드 — 사용자 화면 노출 식별자. actor_id (UUID) 와 분리';

COMMENT ON COLUMN slip_audit_logs.actor_color IS
    'FE userIdToColor 결과 backup (HSL hex). NULL 허용 — FE 가 client side 에서 재계산 가능';

COMMENT ON COLUMN slip_audit_logs.field_name IS
    'JSON-path-like 필드 식별자. 헤더 = "memo"/"shippingAddress" 등, 라인 = "lines[idx].quantity" 등';

COMMENT ON COLUMN slip_audit_logs.old_value IS
    '이전 값 (취소선 표시용). NULL = 신규 필드/라인 (이전 값 없음)';

COMMENT ON COLUMN slip_audit_logs.new_value IS
    '새 값. NULL = 라인 삭제 등';

-- 슬립별 audit 조회 + 최신 revision 우선 인덱스 (FE timeline 표시)
CREATE INDEX ix_slip_audit_logs_slip
    ON slip_audit_logs (slip_id, revision_no DESC, changed_at DESC)
    WHERE is_deleted = FALSE;

-- 사용자별 audit 조회 (감사 추적 / 활동 통계)
CREATE INDEX ix_slip_audit_logs_actor
    ON slip_audit_logs (actor_id, changed_at DESC)
    WHERE is_deleted = FALSE;

-- slips.revision_count — 누적 수정 횟수 (다음 revision_no 채번용 + FE 표시).
-- 기존 row 는 0 으로 backfill (NOT NULL 안전). 본 PR 부터 SlipPublishService.editHeader
-- 등 mutation 시 +1 증가.
ALTER TABLE slips ADD COLUMN revision_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN slips.revision_count IS
    'PR-H2 누적 수정 횟수 — slip_audit_logs 의 다음 revision_no 채번 보조 + FE timeline UI 표시';
