-- DevOps 검증용 V3/V4 드래프트 (BE 가 작성할 마이그레이션의 PgSQL 호환성 사전 시연).
-- Plan §5.1 / §5.2 그대로 옮겨 PostgreSQL 16 컨테이너에서 V1 + V2 다음 순차 실행 검증.

-- V3__add_slip_driver_contact.sql
ALTER TABLE slips ADD COLUMN driver_name  VARCHAR(50);
ALTER TABLE slips ADD COLUMN driver_phone VARCHAR(20);
ALTER TABLE slips ADD COLUMN delivery_batch_id UUID;
CREATE INDEX ix_slips_delivery_batch ON slips (delivery_batch_id) WHERE delivery_batch_id IS NOT NULL;
CREATE INDEX ix_slips_driver_phone_date ON slips (driver_phone, slip_date) WHERE driver_phone IS NOT NULL;

-- V4__create_delivery_batches.sql
CREATE TABLE delivery_batches (
    id UUID PRIMARY KEY,
    batch_token VARCHAR(64) NOT NULL,
    driver_name VARCHAR(50) NOT NULL,
    driver_phone VARCHAR(20) NOT NULL,
    batch_date DATE NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    sms_sent_at TIMESTAMP,
    sms_last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    modified_at TIMESTAMP,
    modified_by VARCHAR(50),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_batch_token UNIQUE (batch_token)
);

CREATE UNIQUE INDEX uk_delivery_batches_driver_date
    ON delivery_batches (driver_phone, batch_date)
    WHERE is_deleted = FALSE;

ALTER TABLE slips
    ADD CONSTRAINT fk_slips_delivery_batch
    FOREIGN KEY (delivery_batch_id) REFERENCES delivery_batches(id);
