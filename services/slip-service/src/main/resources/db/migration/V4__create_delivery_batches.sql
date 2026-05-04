-- V4__create_delivery_batches.sql
-- Slip Service — Slice B (notification-slice-B Plan §5.2):
-- DeliveryBatch 신규 테이블 — 같은 driverPhone + batchDate 슬립을 단일 토큰으로 묶는 그룹.
--
-- partial unique index `(driver_phone, batch_date) WHERE is_deleted=false` —
-- 기사 1명이 같은 날 받을 SMS 는 항상 1건 (중복 그룹 방지).
-- batch_token UNIQUE — 토큰 충돌 방지.

----------------------------------------------------------------------
-- 1) delivery_batches — 배송 배치 헤더
----------------------------------------------------------------------
CREATE TABLE delivery_batches (
    id                 UUID         PRIMARY KEY,
    batch_token        VARCHAR(64)  NOT NULL,
    driver_name        VARCHAR(50)  NOT NULL,
    driver_phone       VARCHAR(20)  NOT NULL,
    batch_date         DATE         NOT NULL,
    token_expires_at   TIMESTAMP    NOT NULL,
    sms_sent_at        TIMESTAMP,
    sms_last_error     VARCHAR(500),

    -- BaseEntity audit (V1 mirror)
    created_at         TIMESTAMP    NOT NULL,
    created_by         VARCHAR(50)  NOT NULL,
    modified_at        TIMESTAMP,
    modified_by        VARCHAR(50),
    deleted_at         TIMESTAMP,
    deleted_by         VARCHAR(50),
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_delivery_batches_token UNIQUE (batch_token)
);

----------------------------------------------------------------------
-- 2) Partial unique index — (driverPhone, batchDate) 활성 1건만 (중복 그룹 방지)
----------------------------------------------------------------------
CREATE UNIQUE INDEX uk_delivery_batches_driver_date
    ON delivery_batches (driver_phone, batch_date)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 3) Lookup index — date 기반 list 쿼리 가속화
----------------------------------------------------------------------
CREATE INDEX ix_delivery_batches_date
    ON delivery_batches (batch_date, is_deleted);

----------------------------------------------------------------------
-- 4) FK — slips.delivery_batch_id → delivery_batches(id)
--    V3 에서 컬럼만 추가했고 FK 는 본 V4 에서 추가 (양방향 의존성 제거)
----------------------------------------------------------------------
ALTER TABLE slips
    ADD CONSTRAINT fk_slips_delivery_batch
    FOREIGN KEY (delivery_batch_id) REFERENCES delivery_batches(id);
