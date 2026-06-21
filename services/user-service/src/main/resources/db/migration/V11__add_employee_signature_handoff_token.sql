-- V11__add_employee_signature_handoff_token.sql
-- user-service — 사원 서명 모바일 핸드오프 토큰 (slice C1b · spec §4.4).
--
-- 관리자 desktop 이 "모바일로 그리기" 발급 → 1회용 토큰 → 사원 폰 공개 제출.
-- TTL=10분 (서비스 레이어 now+10분 발급, 진입 시 expires_at 비교 검증).
-- token = SecureRandom 48바이트 → base64url 64자 (slip delivery_batches.batch_token 패턴).
--
-- 컬럼 컨벤션 (slip V5 계승):
--   * token VARCHAR(64) — partial 아닌 전체 UNIQUE (NULL 미발생, NOT NULL).
--   * used_at TIMESTAMP NULL — 1회용 소진 마커.
--   * BaseEntity 7 audit 컬럼 (created/modified/deleted + is_deleted).

CREATE TABLE employee_signature_handoff_token (
    id            UUID         PRIMARY KEY,
    employee_id   UUID         NOT NULL,
    token         VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    used_at       TIMESTAMP,
    actor_user_id VARCHAR(50),

    -- BaseEntity 7 audit (V1 컨벤션 그대로)
    created_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(50)  NOT NULL,
    modified_at   TIMESTAMP,
    modified_by   VARCHAR(50),
    deleted_at    TIMESTAMP,
    deleted_by    VARCHAR(50),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 공개 제출 토큰 lookup 유일성 — soft-delete 무효화 후 같은 token 재발급은 없으므로 전체 UNIQUE.
CREATE UNIQUE INDEX uk_emp_sig_handoff_token
    ON employee_signature_handoff_token (token);

-- 재발급 시 동일 사원 미사용 토큰 무효화 lookup 가속.
CREATE INDEX ix_emp_sig_handoff_employee_open
    ON employee_signature_handoff_token (employee_id)
    WHERE used_at IS NULL AND is_deleted = FALSE;
