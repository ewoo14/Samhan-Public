-- V4__create_blocked_partners.sql
-- Phase 10 PR-D Part B — BLOCK 발송금지 거래처 (Samhan Public 알림 발송 차단 가드).
--
-- 본 테이블은 PR-E 알림 발송 (chat / push) 진입점에서
-- existsByPartnerCodeAndIsDeletedFalse(partnerCode) 가드로 사용. 차단 해제 = soft-delete
-- (BaseEntity.markDeleted) — 활성 행 partial unique index 가 partnerCode 재차단 허용.
--
-- 사용자 명시: business_name 은 snapshot only — 진실의 원천은 partner_code (+ partners.name).
-- Notion CSV import 시 partner-service.PartnerService.findByName 으로 partnerCode 역추적.
--
-- 컬럼 컨벤션 (V1 partners 와 동일):
--   * 짧은 문자열 VARCHAR(N)
--   * BaseEntity 7 audit fields (created_at / created_by / modified_at / modified_by /
--     deleted_at / deleted_by / is_deleted)

CREATE TABLE blocked_partners (
    id                              UUID         PRIMARY KEY,
    partner_code                    VARCHAR(50)  NOT NULL,
    partner_business_name_snapshot  VARCHAR(200) NOT NULL,
    block_reason                    VARCHAR(500),
    blocked_at                      TIMESTAMP    NOT NULL,
    source                          VARCHAR(20)  NOT NULL DEFAULT 'NOTION_IMPORT'
        CHECK (source IN ('NOTION_IMPORT','MANUAL','LEGACY_GAS')),

    created_at                      TIMESTAMP    NOT NULL,
    created_by                      VARCHAR(50)  NOT NULL,
    modified_at                     TIMESTAMP,
    modified_by                     VARCHAR(50),
    deleted_at                      TIMESTAMP,
    deleted_by                      VARCHAR(50),
    is_deleted                      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 동일 partner_code 활성 BLOCK row 중복 방지 (해제 후 재차단 허용)
CREATE UNIQUE INDEX ux_blocked_partner_code_active
    ON blocked_partners (partner_code)
    WHERE is_deleted = FALSE;

-- PR-E 알림 발송 가드용 lookup 인덱스 — partner_code 단일 컬럼
CREATE INDEX ix_blocked_partners_partner_code
    ON blocked_partners (partner_code)
    WHERE is_deleted = FALSE;

-- source 별 통계 / admin 화면 필터
CREATE INDEX ix_blocked_partners_source_blocked_at
    ON blocked_partners (source, blocked_at DESC)
    WHERE is_deleted = FALSE;
