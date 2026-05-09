-- V2__add_partner_chat_room_mapping.sql
-- PR-D Part 2-3 — CHAT 단톡방 매핑 (Samhan Public 프로그램 native 이식 — Notion DB 111 매핑 시드).
--
-- 매핑 의미: 거래처(partner) ↔ 카톡 단톡방(chat_room_name) N:M (1 거래처가 여러 발주방, 1 발주방에
-- 여러 거래처 가능 — 합병/대표 분리 케이스). 본 V2 는 매핑 행만 유지, 발주서 발송 시 단톡방 이름으로
-- 그룹 라우팅 (실 채널 연동은 별도 phase).
--
-- 사용자 명시: "추후 거래처명이 아니라 거래처코드로 매핑할 수 있도록"
--   → partner_code 가 source-of-truth (논리 FK, partner-service DB 분리이므로 물리 FK 미설정).
--   → partner_business_name_snapshot 은 import 시점 사업자명 (감사용, drift 무시).
--
-- BaseEntity 7 audit fields (created_at/created_by/modified_at/modified_by/deleted_at/deleted_by/is_deleted).
-- Soft Delete 만 — 삭제는 application 레벨 markDeleted() + partial unique index (is_deleted=FALSE).

CREATE TABLE partner_chat_room_mappings (
    id                              UUID         PRIMARY KEY,
    partner_code                    VARCHAR(50)  NOT NULL,
    partner_business_name_snapshot  VARCHAR(200) NOT NULL,
    chat_room_name                  VARCHAR(200) NOT NULL,
    source                          VARCHAR(20)  NOT NULL DEFAULT 'NOTION_IMPORT',
    notion_created_at               TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 활성 행 한정 — (partner_code, chat_room_name) 동일 매핑 중복 방지. soft-delete 후 재등록 허용.
CREATE UNIQUE INDEX ux_chat_mapping_partner_room_active
    ON partner_chat_room_mappings (partner_code, chat_room_name)
    WHERE is_deleted = FALSE;

-- chat_room_name 으로 그룹 조회 (발주 단톡방 → 매핑된 거래처 리스트)
CREATE INDEX ix_chat_mapping_room_active
    ON partner_chat_room_mappings (chat_room_name)
    WHERE is_deleted = FALSE;

-- partner_code 으로 단톡방 조회 (거래처 → 단톡방 N개 매핑)
CREATE INDEX ix_chat_mapping_partner_active
    ON partner_chat_room_mappings (partner_code)
    WHERE is_deleted = FALSE;
