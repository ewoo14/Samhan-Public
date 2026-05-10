-- V16__add_slip_ecount_schema.sql
-- Slip Service — PR-G1 BE: e-Count schema 보강 (12 컬럼) + e-Count API 호출 코드 완전 제거.
--
-- 컨텍스트:
--   * 이전 분석 (ad63ed33) 결과 — legacy GAS 가 e-Count API 로 출고전표 발행 시 사용한
--     14 BulkDatas 필드 중 12 필드가 우리 slip-service 에 누락되어 있었다.
--   * DTO 는 받지만 저장되지 않거나 (U_MEMO1/2/3 등) memo 1000자 prepend 처리 (별도 컬럼 없음).
--   * 사용자 결정 (Samhan Public native) — e-Count API 호출 코드 완전 제거 + schema 보강.
--   * 자체 슬립번호 채번 + 자체 publish 흐름으로 완결, e-Count 의존 0.
--
-- 신규 컬럼 매핑 (legacy e-Count BulkDatas 필드 → 우리 slip 컬럼):
--   1) io_type                  VARCHAR(2)   '10'=출고 / '11'=입고. legacy IO_TYPE.
--   2) time_date                VARCHAR(8)   HHmmss 형식. legacy TIME_DATE.
--   3) customer_tel             VARCHAR(50)  거래처 연락처 snapshot. legacy U_MEMO1.
--   4) customer_address         VARCHAR(500) 거래처 사업장 주소. legacy U_MEMO2.
--   5) customer_representative  VARCHAR(100) 거래처 대표자명. legacy U_MEMO3.
--   6) shipping_address         VARCHAR(500) 배송지 주소. legacy U_TXT1.
--   7) inspection_address       VARCHAR(500) 검수지 주소. legacy ADD_TXT_01_T.
--   8) receiver_phone           VARCHAR(50)  수령자 연락처. legacy ADD_TXT_03_T.
--   9) payment_due_label        VARCHAR(20)  결제 만기 라벨 (MM-DD). legacy ADD_TXT_05_T.
--  10) discount_info            VARCHAR(200) 할인 정보 (자유 텍스트). legacy ADD_TXT_06_T.
--  11) collect_term             VARCHAR(50)  대금 회수 조건 (월말/익월말 등). legacy COLL_TERM.
--  12) agree_term               VARCHAR(50)  거래 약정 조건. legacy AGREE_TERM.
--
-- 컬럼 컨벤션:
--   * io_type DEFAULT '10' — 신규 row 가 OUTBOUND 기본 (현 publish endpoint OUTBOUND 한정).
--   * 나머지 11 컬럼 nullable, 기존 row backfill 없음 (legacy 호환).
--   * VARCHAR 길이는 legacy GAS 의 e-Count API payload 한도 기준 산정 (충분 마진).
--
-- 회귀 영향:
--   * io_type 외 11 컬럼 nullable, 기존 IT 영향 0.
--   * V15 의 partner_code / classified_region_group 와 직교 — 별도 add column.
--   * SlipPublishService 의 composeMemo 리팩토링과 함께 적용 (memo 1000자 prepend → 별도 컬럼 직접 저장).

----------------------------------------------------------------------
-- 1) slips 신규 컬럼 12종
----------------------------------------------------------------------
ALTER TABLE slips
    ADD COLUMN io_type                  VARCHAR(2)   DEFAULT '10',
    ADD COLUMN time_date                VARCHAR(8),
    ADD COLUMN customer_tel             VARCHAR(50),
    ADD COLUMN customer_address         VARCHAR(500),
    ADD COLUMN customer_representative  VARCHAR(100),
    ADD COLUMN shipping_address         VARCHAR(500),
    ADD COLUMN inspection_address       VARCHAR(500),
    ADD COLUMN receiver_phone           VARCHAR(50),
    ADD COLUMN payment_due_label        VARCHAR(20),
    ADD COLUMN discount_info            VARCHAR(200),
    ADD COLUMN collect_term             VARCHAR(50),
    ADD COLUMN agree_term               VARCHAR(50);

----------------------------------------------------------------------
-- 2) 컬럼 코멘트 — legacy BulkDatas 매핑 추적
----------------------------------------------------------------------
COMMENT ON COLUMN slips.io_type IS
    'PR-G1 입출고 구분 — 10=출고, 11=입고. legacy e-Count BulkDatas IO_TYPE';
COMMENT ON COLUMN slips.time_date IS
    'PR-G1 발행 시각 HHmmss — legacy e-Count BulkDatas TIME_DATE';
COMMENT ON COLUMN slips.customer_tel IS
    'PR-G1 거래처 연락처 snapshot — legacy U_MEMO1';
COMMENT ON COLUMN slips.customer_address IS
    'PR-G1 거래처 사업장 주소 snapshot — legacy U_MEMO2';
COMMENT ON COLUMN slips.customer_representative IS
    'PR-G1 거래처 대표자명 snapshot — legacy U_MEMO3';
COMMENT ON COLUMN slips.shipping_address IS
    'PR-G1 배송지 주소 — legacy U_TXT1 (memo prepend 폐기, 별도 컬럼)';
COMMENT ON COLUMN slips.inspection_address IS
    'PR-G1 검수지 주소 — legacy ADD_TXT_01_T (memo prepend 폐기, 별도 컬럼)';
COMMENT ON COLUMN slips.receiver_phone IS
    'PR-G1 수령자 연락처 — legacy ADD_TXT_03_T (memo prepend 폐기, 별도 컬럼)';
COMMENT ON COLUMN slips.payment_due_label IS
    'PR-G1 결제 만기 라벨 MM-DD — legacy ADD_TXT_05_T';
COMMENT ON COLUMN slips.discount_info IS
    'PR-G1 할인 정보 자유 텍스트 — legacy ADD_TXT_06_T';
COMMENT ON COLUMN slips.collect_term IS
    'PR-G1 대금 회수 조건 — legacy COLL_TERM';
COMMENT ON COLUMN slips.agree_term IS
    'PR-G1 거래 약정 조건 — legacy AGREE_TERM';
