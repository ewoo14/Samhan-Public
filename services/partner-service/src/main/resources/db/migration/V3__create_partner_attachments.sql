-- V3__create_partner_attachments.sql
-- partner-service — 거래처 첨부 파일 (사업자등록증/명함/세금계산서/계약서/기타) 신규 테이블.
-- 파일 본체는 MinIO (S3 호환) 에 저장하고 본 row 는 metadata 만 보유.
-- BaseEntity audit 컬럼 매핑: created_at / created_by / modified_at / modified_by /
--   deleted_at / deleted_by / is_deleted (V1 partners 테이블과 동일 컨벤션).
--
-- attachment_type CHECK 제약 = AttachmentType enum 5 값과 1:1.
-- (BIZ_LICENSE / BUSINESS_CARD / TAX_INVOICE / CONTRACT / OTHER)

CREATE TABLE partner_attachments (
    id                  UUID         PRIMARY KEY,
    partner_id          UUID         NOT NULL REFERENCES partners(id),
    attachment_type     VARCHAR(30)  NOT NULL
        CHECK (attachment_type IN ('BIZ_LICENSE','BUSINESS_CARD','TAX_INVOICE','CONTRACT','OTHER')),
    file_name           VARCHAR(200) NOT NULL,
    file_size           BIGINT       NOT NULL,
    mime_type           VARCHAR(100) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    storage_url         VARCHAR(1000),
    description         VARCHAR(500),
    uploaded_by         UUID         NOT NULL,
    uploaded_at         TIMESTAMP    NOT NULL,

    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 거래처별 첨부 목록 조회 — 활성 행만 인덱스
CREATE INDEX ix_partner_attachments_partner_id
    ON partner_attachments (partner_id)
    WHERE is_deleted = FALSE;

-- 첨부 유형별 필터 (예: BIZ_LICENSE 만 조회) — 활성 행만 인덱스
CREATE INDEX ix_partner_attachments_type
    ON partner_attachments (attachment_type)
    WHERE is_deleted = FALSE;

-- storage_key 중복 가드 (MinIO 객체 멱등성) — 활성 행만 unique
CREATE UNIQUE INDEX ux_partner_attachments_storage_key_active
    ON partner_attachments (storage_key)
    WHERE is_deleted = FALSE;
