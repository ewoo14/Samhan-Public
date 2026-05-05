-- V1__init_accounting_service.sql — DRAFT (Slice A DevOps 시연 산출물)
-- BE agent 가 services/accounting-service/src/main/resources/db/migration/
-- V1__init_accounting_service.sql 로 그대로 채택 가능.
--
-- 본 draft 는 Phase 4 accounting-slice-A 의 다음 산출을 보장:
--   1) chart_of_accounts (표준 계정과목 마스터)
--   2) journals (분개 헤더)
--   3) journal_lines (분개 라인 — 차변/대변)
--   4) journal_number_sequences (분개 번호 순번)
--   5) 한국 일반기업회계기준 표준 계정과목 시드 50+ rows
--      (100/200/300/400/500/800/900 코드 체계, project_korean_accounting.md)
--
-- 컬럼 컨벤션은 V1__init_slip_service.sql 답습:
--   * VARCHAR(N), CHAR/bpchar 금지
--   * 금액 NUMERIC(15,2)
--   * UUID PK + version BIGINT NOT NULL DEFAULT 0 (낙관적 락)
--   * BaseEntity 7 audit 필드 (created_at/by, modified_at/by, is_deleted, deleted_at, deleted_by)

----------------------------------------------------------------------
-- 1) chart_of_accounts — 한국 일반기업회계기준 표준 계정과목 마스터
----------------------------------------------------------------------
CREATE TABLE chart_of_accounts (
    id              UUID         PRIMARY KEY,
    account_code    VARCHAR(10)  NOT NULL,
    account_name    VARCHAR(60)  NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,   -- ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE
    parent_code     VARCHAR(10),
    is_leaf         BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system       BOOLEAN      NOT NULL DEFAULT TRUE,   -- 시드 계정 = TRUE, 사용자 추가 = FALSE
    sort_order      INT          NOT NULL DEFAULT 0,
    description     VARCHAR(200),
    version         BIGINT       NOT NULL DEFAULT 0,

    -- BaseEntity audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),

    CONSTRAINT uk_chart_of_accounts_code UNIQUE (account_code)
);

CREATE INDEX idx_chart_of_accounts_type ON chart_of_accounts (account_type);
CREATE INDEX idx_chart_of_accounts_parent ON chart_of_accounts (parent_code);

----------------------------------------------------------------------
-- 2) journals — 분개 헤더
----------------------------------------------------------------------
CREATE TABLE journals (
    id              UUID         PRIMARY KEY,
    journal_no      VARCHAR(30)  NOT NULL,    -- JNL-YYYYMMDD-NNNN
    journal_date    DATE         NOT NULL,
    seq_no          INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL,    -- DRAFT/POSTED/CANCELLED
    description     VARCHAR(500),
    source_type     VARCHAR(30),              -- MANUAL/SLIP/RECURRING/CLOSING
    source_ref_id   UUID,                     -- slip_id 등 출처 참조 (FK 없음, 서비스 경계)
    posted_at       TIMESTAMP,
    posted_by       VARCHAR(50),
    version         BIGINT       NOT NULL DEFAULT 0,

    -- BaseEntity audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),

    CONSTRAINT uk_journals_no UNIQUE (journal_no)
);

CREATE INDEX idx_journals_date ON journals (journal_date DESC);
CREATE INDEX idx_journals_status ON journals (status);
CREATE INDEX idx_journals_source ON journals (source_type, source_ref_id);

----------------------------------------------------------------------
-- 3) journal_lines — 분개 라인 (차변/대변)
----------------------------------------------------------------------
CREATE TABLE journal_lines (
    id              UUID         PRIMARY KEY,
    journal_id      UUID         NOT NULL,
    line_no         INT          NOT NULL,
    account_code    VARCHAR(10)  NOT NULL,
    debit_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_memo       VARCHAR(200),
    partner_id      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    -- BaseEntity audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),

    CONSTRAINT fk_journal_lines_journal FOREIGN KEY (journal_id) REFERENCES journals(id),
    CONSTRAINT chk_journal_lines_dr_cr CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (debit_amount = 0 AND credit_amount > 0)
    )
);

CREATE INDEX idx_journal_lines_journal ON journal_lines (journal_id);
CREATE INDEX idx_journal_lines_account ON journal_lines (account_code);

----------------------------------------------------------------------
-- 4) journal_number_sequences — 분개번호 일자별 순번 (slip_number_sequences 답습)
----------------------------------------------------------------------
CREATE TABLE journal_number_sequences (
    journal_date    DATE         PRIMARY KEY,
    last_seq_no     INT          NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    modified_at     TIMESTAMP
);

----------------------------------------------------------------------
-- 5) 표준 계정과목 시드 — 한국 일반기업회계기준 (50+ rows)
--    project_korean_accounting.md 메모리 의무 코드 체계:
--    100=자산  200=부채  300=자본  400=수익  500=비용  800=수익외  900=비용외
----------------------------------------------------------------------
INSERT INTO chart_of_accounts
  (id, account_code, account_name, account_type, parent_code, is_leaf, sort_order, created_at, created_by) VALUES

  -- 100 자산 (ASSET) ----------------------------------------------------
  (gen_random_uuid(), '100', '자산',           'ASSET', NULL,  FALSE, 100, NOW(), 'system'),
  (gen_random_uuid(), '101', '현금',           'ASSET', '100', TRUE,  101, NOW(), 'system'),
  (gen_random_uuid(), '102', '당좌예금',       'ASSET', '100', TRUE,  102, NOW(), 'system'),
  (gen_random_uuid(), '103', '보통예금',       'ASSET', '100', TRUE,  103, NOW(), 'system'),
  (gen_random_uuid(), '108', '외상매출금',     'ASSET', '100', TRUE,  108, NOW(), 'system'),
  (gen_random_uuid(), '110', '받을어음',       'ASSET', '100', TRUE,  110, NOW(), 'system'),
  (gen_random_uuid(), '120', '미수금',         'ASSET', '100', TRUE,  120, NOW(), 'system'),
  (gen_random_uuid(), '131', '선급금',         'ASSET', '100', TRUE,  131, NOW(), 'system'),
  (gen_random_uuid(), '146', '상품',           'ASSET', '100', TRUE,  146, NOW(), 'system'),
  (gen_random_uuid(), '150', '제품',           'ASSET', '100', TRUE,  150, NOW(), 'system'),
  (gen_random_uuid(), '153', '원재료',         'ASSET', '100', TRUE,  153, NOW(), 'system'),
  (gen_random_uuid(), '169', '재공품',         'ASSET', '100', TRUE,  169, NOW(), 'system'),
  (gen_random_uuid(), '202', '건물',           'ASSET', '100', TRUE,  202, NOW(), 'system'),
  (gen_random_uuid(), '208', '차량운반구',     'ASSET', '100', TRUE,  208, NOW(), 'system'),
  (gen_random_uuid(), '212', '비품',           'ASSET', '100', TRUE,  212, NOW(), 'system'),

  -- 200 부채 (LIABILITY) ------------------------------------------------
  (gen_random_uuid(), '200', '부채',           'LIABILITY', NULL,  FALSE, 200, NOW(), 'system'),
  (gen_random_uuid(), '251', '외상매입금',     'LIABILITY', '200', TRUE,  251, NOW(), 'system'),
  (gen_random_uuid(), '252', '지급어음',       'LIABILITY', '200', TRUE,  252, NOW(), 'system'),
  (gen_random_uuid(), '253', '미지급금',       'LIABILITY', '200', TRUE,  253, NOW(), 'system'),
  (gen_random_uuid(), '254', '예수금',         'LIABILITY', '200', TRUE,  254, NOW(), 'system'),
  (gen_random_uuid(), '255', '부가세예수금',   'LIABILITY', '200', TRUE,  255, NOW(), 'system'),
  (gen_random_uuid(), '259', '선수금',         'LIABILITY', '200', TRUE,  259, NOW(), 'system'),
  (gen_random_uuid(), '260', '단기차입금',     'LIABILITY', '200', TRUE,  260, NOW(), 'system'),
  (gen_random_uuid(), '293', '장기차입금',     'LIABILITY', '200', TRUE,  293, NOW(), 'system'),
  (gen_random_uuid(), '295', '퇴직급여충당부채', 'LIABILITY', '200', TRUE, 295, NOW(), 'system'),

  -- 300 자본 (EQUITY) ---------------------------------------------------
  (gen_random_uuid(), '300', '자본',           'EQUITY', NULL,  FALSE, 300, NOW(), 'system'),
  (gen_random_uuid(), '331', '자본금',         'EQUITY', '300', TRUE,  331, NOW(), 'system'),
  (gen_random_uuid(), '341', '주식발행초과금', 'EQUITY', '300', TRUE,  341, NOW(), 'system'),
  (gen_random_uuid(), '375', '이월이익잉여금', 'EQUITY', '300', TRUE,  375, NOW(), 'system'),
  (gen_random_uuid(), '377', '미처분이익잉여금', 'EQUITY', '300', TRUE, 377, NOW(), 'system'),

  -- 400 수익 (REVENUE) --------------------------------------------------
  (gen_random_uuid(), '400', '매출',           'REVENUE', NULL,  FALSE, 400, NOW(), 'system'),
  (gen_random_uuid(), '401', '상품매출',       'REVENUE', '400', TRUE,  401, NOW(), 'system'),
  (gen_random_uuid(), '404', '제품매출',       'REVENUE', '400', TRUE,  404, NOW(), 'system'),
  (gen_random_uuid(), '405', '용역매출',       'REVENUE', '400', TRUE,  405, NOW(), 'system'),
  (gen_random_uuid(), '406', '매출에누리및환입', 'REVENUE', '400', TRUE, 406, NOW(), 'system'),

  -- 500 비용 (EXPENSE — 매출원가/판관비) ---------------------------------
  (gen_random_uuid(), '500', '비용',           'EXPENSE', NULL,  FALSE, 500, NOW(), 'system'),
  (gen_random_uuid(), '451', '상품매출원가',   'EXPENSE', '500', TRUE,  451, NOW(), 'system'),
  (gen_random_uuid(), '455', '제품매출원가',   'EXPENSE', '500', TRUE,  455, NOW(), 'system'),
  (gen_random_uuid(), '801', '급여',           'EXPENSE', '500', TRUE,  801, NOW(), 'system'),
  (gen_random_uuid(), '811', '복리후생비',     'EXPENSE', '500', TRUE,  811, NOW(), 'system'),
  (gen_random_uuid(), '812', '여비교통비',     'EXPENSE', '500', TRUE,  812, NOW(), 'system'),
  (gen_random_uuid(), '813', '접대비',         'EXPENSE', '500', TRUE,  813, NOW(), 'system'),
  (gen_random_uuid(), '814', '통신비',         'EXPENSE', '500', TRUE,  814, NOW(), 'system'),
  (gen_random_uuid(), '815', '수도광열비',     'EXPENSE', '500', TRUE,  815, NOW(), 'system'),
  (gen_random_uuid(), '817', '세금과공과',     'EXPENSE', '500', TRUE,  817, NOW(), 'system'),
  (gen_random_uuid(), '818', '감가상각비',     'EXPENSE', '500', TRUE,  818, NOW(), 'system'),
  (gen_random_uuid(), '819', '임차료',         'EXPENSE', '500', TRUE,  819, NOW(), 'system'),
  (gen_random_uuid(), '820', '수선비',         'EXPENSE', '500', TRUE,  820, NOW(), 'system'),
  (gen_random_uuid(), '821', '보험료',         'EXPENSE', '500', TRUE,  821, NOW(), 'system'),
  (gen_random_uuid(), '824', '운반비',         'EXPENSE', '500', TRUE,  824, NOW(), 'system'),
  (gen_random_uuid(), '826', '도서인쇄비',     'EXPENSE', '500', TRUE,  826, NOW(), 'system'),
  (gen_random_uuid(), '830', '지급수수료',     'EXPENSE', '500', TRUE,  830, NOW(), 'system'),
  (gen_random_uuid(), '831', '광고선전비',     'EXPENSE', '500', TRUE,  831, NOW(), 'system'),
  (gen_random_uuid(), '848', '잡비',           'EXPENSE', '500', TRUE,  848, NOW(), 'system'),

  -- 800 영업외수익 / 900 영업외비용 ---------------------------------------
  (gen_random_uuid(), '901', '이자수익',       'REVENUE', NULL, TRUE, 901, NOW(), 'system'),
  (gen_random_uuid(), '904', '잡이익',         'REVENUE', NULL, TRUE, 904, NOW(), 'system'),
  (gen_random_uuid(), '951', '이자비용',       'EXPENSE', NULL, TRUE, 951, NOW(), 'system'),
  (gen_random_uuid(), '953', '기부금',         'EXPENSE', NULL, TRUE, 953, NOW(), 'system'),
  (gen_random_uuid(), '961', '잡손실',         'EXPENSE', NULL, TRUE, 961, NOW(), 'system');

-- 시드 row 수: 56 rows (assets 15 + liability 10 + equity 5 + revenue 7 + expense 19)
