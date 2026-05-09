-- V14__add_slip_attachment.sql
-- Slip Service — P1-8 (Stage 4): 모바일 사진 첨부 + lock-by-period 컬럼.
--
-- 매뉴얼 출처: docs/manual/04-모바일/04-사진-첨부.md §4.2 (V12 예상 schema)
--
-- 컨텍스트:
--   * slip_attachments 테이블 신규 — 슬립 1건당 N 첨부 (배송 사진 / 검수 사진 / 견적 현장 사진)
--   * 실 파일 = MinIO (S3 호환) bucket `slip-attachments`. 본 row 는 metadata + EXIF GPS 만 보관.
--   * partner-service 의 partner_attachments 테이블 패턴 그대로 복제 + EXIF GPS / capturedAt 추가.
--
--   * slips.lock_flag — accounting-service 마감 lock 컬럼. 기간 마감 후 해당 기간 CONFIRMED 슬립
--     일괄 lock_flag = true → 이후 reject/cancel 차단 (도메인 메서드가 가드).
--
-- 컬럼 컨벤션:
--   * 짧은 문자열 VARCHAR(N), CHAR/bpchar 금지
--   * 위도/경도 NUMERIC(10,7) — 소수점 7자리 (1.1cm 정밀도, 한국 좌표계 충분)
--   * 파일 크기 BIGINT (PartnerAttachment 와 동일 — Java Long 매핑)
--
-- 회귀 영향:
--   * slip_attachments 신규 — 기존 IT 영향 0
--   * lock_flag DEFAULT FALSE — 기존 row 자동 backfill, IT 의 reject/cancel 시나리오는
--     모두 lock_flag = false 상태로 진행 (영향 0)

----------------------------------------------------------------------
-- 1) slip_attachments — 슬립 첨부 파일 (배송 사진 / 검수 사진 / 견적 현장 사진)
----------------------------------------------------------------------
CREATE TABLE slip_attachments (
    id                  UUID         PRIMARY KEY,
    slip_id             UUID         NOT NULL REFERENCES slips(id),
    attachment_type     VARCHAR(20)  NOT NULL,
    file_name           VARCHAR(200) NOT NULL,
    file_size           BIGINT       NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    storage_url         VARCHAR(1000),
    exif_gps_lat        NUMERIC(10,7),
    exif_gps_lng        NUMERIC(10,7),
    captured_at         TIMESTAMP,
    uploaded_by         VARCHAR(50)  NOT NULL,
    uploaded_at         TIMESTAMP    NOT NULL,

    -- BaseEntity 7 audit
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE slip_attachments IS
    'P1-8 슬립 첨부 파일 — DELIVERY (배송 사진) / INSPECTION (검수 사진) / ESTIMATE (견적 현장 사진)';

COMMENT ON COLUMN slip_attachments.exif_gps_lat IS
    'EXIF GPS 위도 (선택) — 모바일 카메라 촬영 시 자동 추출, mobile-staff 가 metadata 첨부';

COMMENT ON COLUMN slip_attachments.exif_gps_lng IS
    'EXIF GPS 경도 (선택) — 분쟁 시 정확한 촬영 위치 증빙';

COMMENT ON COLUMN slip_attachments.captured_at IS
    '실 촬영 시각 (선택, EXIF DateTime). 미입력 시 uploaded_at 사용';

CREATE INDEX ix_slip_attachments_slip_active
    ON slip_attachments (slip_id, is_deleted);

CREATE INDEX ix_slip_attachments_type_active
    ON slip_attachments (attachment_type, is_deleted);

----------------------------------------------------------------------
-- 2) slips.lock_flag — accounting-service 마감 lock
--    accounting-service 가 /slips/lock-by-period Feign 호출 시 일괄 update.
--    lock_flag = true 이면 reject/cancel 도메인 메서드가 CONFLICT 던짐 (Slip.requireNotLocked).
----------------------------------------------------------------------
ALTER TABLE slips
    ADD COLUMN lock_flag BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN slips.lock_flag IS
    'accounting-service 마감 lock — true 면 reject/cancel 차단. 마감 기간 CONFIRMED 슬립만 적용';

CREATE INDEX ix_slips_lock_flag_active
    ON slips (lock_flag, is_deleted)
    WHERE lock_flag = TRUE;
