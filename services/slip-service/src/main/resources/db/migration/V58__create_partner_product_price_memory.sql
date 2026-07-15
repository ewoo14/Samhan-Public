-- 거래처+품목 최근 수동단가 기억.
-- 단가는 전표/견적 입력 필드와 동일한 VAT 포함 단가로 저장한다.

CREATE TABLE partner_product_price_memory (
    id              UUID           PRIMARY KEY,
    partner_id      UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    unit_price      NUMERIC(15, 2) NOT NULL,
    source          VARCHAR(30)    NOT NULL,
    remembered_at   TIMESTAMP      NOT NULL,

    -- BaseEntity 7 audit
    created_at      TIMESTAMP      NOT NULL,
    created_by      VARCHAR(50)    NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT ux_partner_product_price_memory_pair UNIQUE (partner_id, product_id)
);

COMMENT ON TABLE partner_product_price_memory IS
    '거래처+품목 최근 수동단가 기억 — partner/product UUID 는 내부 payload 전용이며 화면 식별자로 노출하지 않는다';

COMMENT ON COLUMN partner_product_price_memory.unit_price IS
    '전표/견적 입력 필드와 동일한 VAT 포함 단가. 자동채움 시 그대로 라운드트립한다';

COMMENT ON COLUMN partner_product_price_memory.remembered_at IS
    '원 전표/견적 트랜잭션의 논리 저장 시각. afterCommit 실행 순서와 무관한 최신성 판정 기준';
