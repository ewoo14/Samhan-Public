-- V2__add_ecount_partner_fields.sql
-- 이카운트 27 필드 호환을 위한 Partner 컬럼 보강 (Stage 1 local-test seed 사전 작업).
-- 출처: docs/migration/ecount-reference/091522~091604 (거래처 4 탭 캡처)
--
-- 가드:
--   - 모든 신규 컬럼 NULLable (legacy data 마이그 호환)
--   - 단가 그룹/여신 관련 default 'BASIC' (이카운트 default '기본설정' 매핑)
--   - 한국어 enum 값은 추후 코드테이블 entity 분리 가능성 — 현 단계 VARCHAR

ALTER TABLE partners
    -- 기본 탭 (091522)
    ADD COLUMN sub_biz_no               VARCHAR(20),                          -- 종사업장번호 (4자리)
    ADD COLUMN representative           VARCHAR(50),                          -- 대표자명
    ADD COLUMN business_type            VARCHAR(50),                          -- 업태
    ADD COLUMN industry                 VARCHAR(50),                          -- 종목
    ADD COLUMN fax                      VARCHAR(30),                          -- FAX
    ADD COLUMN email                    VARCHAR(120),                         -- email 1
    ADD COLUMN email2                   VARCHAR(120),                         -- email 2
    ADD COLUMN mobile                   VARCHAR(30),                          -- 휴대전화

    -- 거래처정보 탭 (091540) — 주소 + 검색 키워드 + 그룹 + website
    ADD COLUMN zip_code1                VARCHAR(10),                          -- 우편번호 1 (본사)
    ADD COLUMN address1                 VARCHAR(500),                         -- 주소 1 (본사)
    ADD COLUMN zip_code2                VARCHAR(10),                          -- 우편번호 2 (배송지)
    ADD COLUMN address2                 VARCHAR(500),                         -- 주소 2 (배송지)
    ADD COLUMN search_keyword           VARCHAR(500),                         -- 검색용 키워드
    ADD COLUMN partner_group1           VARCHAR(50),                          -- 거래처분류1 (VIP/일반/신규)
    ADD COLUMN partner_group2           VARCHAR(50),                          -- 거래처분류2 (지역)
    ADD COLUMN website                  VARCHAR(255),                         -- 홈페이지

    -- 여신단가 탭 (091551)
    ADD COLUMN currency                 VARCHAR(8) NOT NULL DEFAULT 'KRW',    -- 통화
    ADD COLUMN shipment_target          BOOLEAN NOT NULL DEFAULT TRUE,        -- 출하 대상 여부
    ADD COLUMN sales_type               VARCHAR(20) NOT NULL DEFAULT '기본설정',  -- 판매유형
    ADD COLUMN purchase_type            VARCHAR(20) NOT NULL DEFAULT '기본설정',  -- 구매유형
    ADD COLUMN receivable_no_mgmt       VARCHAR(20) NOT NULL DEFAULT '기본설정',  -- 매출계정 관리
    ADD COLUMN payable_no_mgmt          VARCHAR(20) NOT NULL DEFAULT '기본설정',  -- 매입계정 관리
    ADD COLUMN outbound_adjustment_rate NUMERIC(5,4) NOT NULL DEFAULT 0,      -- 출고조정률 (0~5%)
    ADD COLUMN inbound_adjustment_rate  NUMERIC(5,4) NOT NULL DEFAULT 0,      -- 입고조정률 (0~5%)
    ADD COLUMN sales_price_group        VARCHAR(50),                          -- 판매단가그룹 (VIP/일반/신규)
    ADD COLUMN purchase_price_group     VARCHAR(50),                          -- 구매단가그룹
    ADD COLUMN credit_period_days       INT,                                  -- 여신기간 (일)
    ADD COLUMN payment_due_days         INT,                                  -- 결제기한 (일)

    -- 부가정보 탭 (091604) — 등록일자 (audit created_at 와 별도 — 회계상 거래 시작일)
    ADD COLUMN registration_date        DATE                                  -- 등록일자 (이카운트 거래시작)
;

-- 검색 보조 인덱스
CREATE INDEX ix_partners_partner_group1 ON partners (partner_group1)
    WHERE is_deleted = FALSE AND partner_group1 IS NOT NULL;
CREATE INDEX ix_partners_partner_group2 ON partners (partner_group2)
    WHERE is_deleted = FALSE AND partner_group2 IS NOT NULL;
CREATE INDEX ix_partners_sales_price_group ON partners (sales_price_group)
    WHERE is_deleted = FALSE AND sales_price_group IS NOT NULL;
CREATE INDEX ix_partners_search_keyword ON partners (search_keyword);

-- 단가그룹 / 판매유형 enum 값 가드 (한국어 코드 — 이카운트 default 매핑)
ALTER TABLE partners
    ADD CONSTRAINT chk_partners_currency CHECK (currency IN ('KRW','USD','JPY','CNY','EUR'));
