-- V1__init_accounting_service.sql
-- Accounting Service — initial schema for ChartOfAccount + Journal + JournalLine + JournalNumberSequence
-- (Plan §2 + §3 + §4). 한국 일반기업회계기준 표준 계정과목 시드 50+ 행 포함.
--
-- BaseEntity audit columns mirror inventory/slip-service.V1 정확히.
-- Soft-delete 는 application-side 의 @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션:
--   * 짧은 문자열은 모두 VARCHAR(N), CHAR/bpchar 금지
--   * 금액은 NUMERIC(15,2)
--   * 낙관적 락: version BIGINT NOT NULL DEFAULT 0

----------------------------------------------------------------------
-- 1) chart_of_accounts — 계정과목 마스터 (한국 표준 코드 PK)
----------------------------------------------------------------------
CREATE TABLE chart_of_accounts (
    code            VARCHAR(6)   PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    category        VARCHAR(30)  NOT NULL,
    parent_code     VARCHAR(6),
    is_leaf         BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INT          NOT NULL,

    -- BaseEntity audit
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_chart_of_accounts_category_active
    ON chart_of_accounts (category, is_deleted);

CREATE INDEX ix_chart_of_accounts_parent_active
    ON chart_of_accounts (parent_code, is_deleted);

----------------------------------------------------------------------
-- 2) journal_number_sequences — 일자별 분개번호 채번 시퀀스
--    SlipNumberSequence 답습. journal_date UNIQUE.
----------------------------------------------------------------------
CREATE TABLE journal_number_sequences (
    id              UUID         PRIMARY KEY,
    journal_date    DATE         NOT NULL,
    last_seq        INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ux_journal_number_sequences_date UNIQUE (journal_date)
);

----------------------------------------------------------------------
-- 3) journals — 분개장 헤더 (DRAFT → POSTED → REVERSED)
----------------------------------------------------------------------
CREATE TABLE journals (
    id                      UUID         PRIMARY KEY,
    journal_no              VARCHAR(20)  NOT NULL,
    journal_date            DATE         NOT NULL,
    description             VARCHAR(500),
    source_type             VARCHAR(20)  NOT NULL,
    source_ref_id           UUID,
    status                  VARCHAR(20)  NOT NULL,
    posted_at               TIMESTAMP,
    posted_by               VARCHAR(50),
    reversed_journal_id     UUID,
    version                 BIGINT       NOT NULL DEFAULT 0,

    created_at              TIMESTAMP    NOT NULL,
    created_by              VARCHAR(50)  NOT NULL,
    modified_at             TIMESTAMP,
    modified_by             VARCHAR(50),
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(50),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_journals_journal_no_active
    ON journals (journal_no)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_journals_date_status_active
    ON journals (journal_date, status, is_deleted);

CREATE INDEX ix_journals_status_active
    ON journals (status, is_deleted);

CREATE INDEX ix_journals_source_active
    ON journals (source_type, source_ref_id, is_deleted);

----------------------------------------------------------------------
-- 4) journal_lines — 분개 라인 (cascade ALL, orphanRemoval)
--    accountCode 는 chart_of_accounts logical reference (FK 강제 X)
--    차변/대변 동시 0 금지 (CHECK)
----------------------------------------------------------------------
CREATE TABLE journal_lines (
    id              UUID            PRIMARY KEY,
    journal_id      UUID            NOT NULL REFERENCES journals(id),
    line_no         INT             NOT NULL,
    account_code    VARCHAR(6)      NOT NULL,
    debit_amount    NUMERIC(15,2)   NOT NULL DEFAULT 0 CHECK (debit_amount >= 0),
    credit_amount   NUMERIC(15,2)   NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    partner_id      UUID,
    memo            VARCHAR(500),

    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_journal_lines_amount_xor
        CHECK ((debit_amount > 0 AND credit_amount = 0)
            OR (debit_amount = 0 AND credit_amount > 0))
);

CREATE INDEX ix_journal_lines_journal_active
    ON journal_lines (journal_id, is_deleted);

CREATE INDEX ix_journal_lines_account_active
    ON journal_lines (account_code, is_deleted);

CREATE INDEX ix_journal_lines_partner_active
    ON journal_lines (partner_id, is_deleted);

----------------------------------------------------------------------
-- 5) 한국 일반기업회계기준 표준 계정과목 시드 (Plan §3 + 메모리)
--    50+ 행, 7-그룹 (100/200/300/400/500/800/900).
--    audit 컬럼은 SYSTEM seed 표시.
----------------------------------------------------------------------

-- ===== 100 자산 (ASSET) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('100',  '자산',         'ASSET',     NULL,  FALSE, 1000, CURRENT_TIMESTAMP, 'SYSTEM'),
('101',  '현금',         'ASSET',     '100', TRUE,  1010, CURRENT_TIMESTAMP, 'SYSTEM'),
('102',  '보통예금',     'ASSET',     '100', TRUE,  1020, CURRENT_TIMESTAMP, 'SYSTEM'),
('103',  '당좌예금',     'ASSET',     '100', TRUE,  1030, CURRENT_TIMESTAMP, 'SYSTEM'),
('104',  '정기예금',     'ASSET',     '100', TRUE,  1040, CURRENT_TIMESTAMP, 'SYSTEM'),
('105',  '정기적금',     'ASSET',     '100', TRUE,  1050, CURRENT_TIMESTAMP, 'SYSTEM'),
('108',  '단기매매증권', 'ASSET',     '100', TRUE,  1080, CURRENT_TIMESTAMP, 'SYSTEM'),
('110',  '외상매출금',   'ASSET',     '100', TRUE,  1100, CURRENT_TIMESTAMP, 'SYSTEM'),
('111',  '받을어음',     'ASSET',     '100', TRUE,  1110, CURRENT_TIMESTAMP, 'SYSTEM'),
('114',  '단기대여금',   'ASSET',     '100', TRUE,  1140, CURRENT_TIMESTAMP, 'SYSTEM'),
('120',  '미수금',       'ASSET',     '100', TRUE,  1200, CURRENT_TIMESTAMP, 'SYSTEM'),
('122',  '소모품',       'ASSET',     '100', TRUE,  1220, CURRENT_TIMESTAMP, 'SYSTEM'),
('124',  '선급금',       'ASSET',     '100', TRUE,  1240, CURRENT_TIMESTAMP, 'SYSTEM'),
('125',  '선급비용',     'ASSET',     '100', TRUE,  1250, CURRENT_TIMESTAMP, 'SYSTEM'),
('130',  '상품',         'ASSET',     '100', TRUE,  1300, CURRENT_TIMESTAMP, 'SYSTEM'),
('131',  '제품',         'ASSET',     '100', TRUE,  1310, CURRENT_TIMESTAMP, 'SYSTEM'),
('135',  '부가세대급금', 'ASSET',     '100', TRUE,  1350, CURRENT_TIMESTAMP, 'SYSTEM'),
('141',  '토지',         'ASSET',     '100', TRUE,  1410, CURRENT_TIMESTAMP, 'SYSTEM'),
('142',  '건물',         'ASSET',     '100', TRUE,  1420, CURRENT_TIMESTAMP, 'SYSTEM'),
('146',  '차량운반구',   'ASSET',     '100', TRUE,  1460, CURRENT_TIMESTAMP, 'SYSTEM'),
('148',  '비품',         'ASSET',     '100', TRUE,  1480, CURRENT_TIMESTAMP, 'SYSTEM'),
('163',  '소프트웨어',   'ASSET',     '100', TRUE,  1630, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 200 부채 (LIABILITY) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('200',  '부채',         'LIABILITY', NULL,  FALSE, 2000, CURRENT_TIMESTAMP, 'SYSTEM'),
('201',  '외상매입금',   'LIABILITY', '200', TRUE,  2010, CURRENT_TIMESTAMP, 'SYSTEM'),
('202',  '지급어음',     'LIABILITY', '200', TRUE,  2020, CURRENT_TIMESTAMP, 'SYSTEM'),
('210',  '미지급금',     'LIABILITY', '200', TRUE,  2100, CURRENT_TIMESTAMP, 'SYSTEM'),
('212',  '미지급비용',   'LIABILITY', '200', TRUE,  2120, CURRENT_TIMESTAMP, 'SYSTEM'),
('220',  '부가세예수금', 'LIABILITY', '200', TRUE,  2200, CURRENT_TIMESTAMP, 'SYSTEM'),
('221',  '예수금',       'LIABILITY', '200', TRUE,  2210, CURRENT_TIMESTAMP, 'SYSTEM'),
('226',  '선수금',       'LIABILITY', '200', TRUE,  2260, CURRENT_TIMESTAMP, 'SYSTEM'),
('230',  '단기차입금',   'LIABILITY', '200', TRUE,  2300, CURRENT_TIMESTAMP, 'SYSTEM'),
('260',  '장기차입금',   'LIABILITY', '200', TRUE,  2600, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 300 자본 (EQUITY) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('300',  '자본',           'EQUITY', NULL,  FALSE, 3000, CURRENT_TIMESTAMP, 'SYSTEM'),
('301',  '자본금',         'EQUITY', '300', TRUE,  3010, CURRENT_TIMESTAMP, 'SYSTEM'),
('320',  '자본잉여금',     'EQUITY', '300', TRUE,  3200, CURRENT_TIMESTAMP, 'SYSTEM'),
('331',  '자기주식',       'EQUITY', '300', TRUE,  3310, CURRENT_TIMESTAMP, 'SYSTEM'),
('341',  '이익잉여금',     'EQUITY', '300', TRUE,  3410, CURRENT_TIMESTAMP, 'SYSTEM'),
('343',  '미처분이익잉여금', 'EQUITY', '300', TRUE,  3430, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 400 매출 (REVENUE) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('400',  '매출',         'REVENUE', NULL,  FALSE, 4000, CURRENT_TIMESTAMP, 'SYSTEM'),
('401',  '상품매출',     'REVENUE', '400', TRUE,  4010, CURRENT_TIMESTAMP, 'SYSTEM'),
('404',  '제품매출',     'REVENUE', '400', TRUE,  4040, CURRENT_TIMESTAMP, 'SYSTEM'),
('405',  '매출에누리',   'REVENUE', '400', TRUE,  4050, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 500 매출원가 (COST_OF_SALES) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('500',  '매출원가',       'COST_OF_SALES', NULL,  FALSE, 5000, CURRENT_TIMESTAMP, 'SYSTEM'),
('501',  '상품매출원가',   'COST_OF_SALES', '500', TRUE,  5010, CURRENT_TIMESTAMP, 'SYSTEM'),
('510',  '제품매출원가',   'COST_OF_SALES', '500', TRUE,  5100, CURRENT_TIMESTAMP, 'SYSTEM'),
('512',  '재료비',         'COST_OF_SALES', '500', TRUE,  5120, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 800 판매비와관리비 (SGA) =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('800',  '판매비와관리비', 'SGA', NULL,  FALSE, 8000, CURRENT_TIMESTAMP, 'SYSTEM'),
('801',  '급여',           'SGA', '800', TRUE,  8010, CURRENT_TIMESTAMP, 'SYSTEM'),
('803',  '상여금',         'SGA', '800', TRUE,  8030, CURRENT_TIMESTAMP, 'SYSTEM'),
('805',  '잡급',           'SGA', '800', TRUE,  8050, CURRENT_TIMESTAMP, 'SYSTEM'),
('806',  '퇴직급여',       'SGA', '800', TRUE,  8060, CURRENT_TIMESTAMP, 'SYSTEM'),
('811',  '복리후생비',     'SGA', '800', TRUE,  8110, CURRENT_TIMESTAMP, 'SYSTEM'),
('812',  '여비교통비',     'SGA', '800', TRUE,  8120, CURRENT_TIMESTAMP, 'SYSTEM'),
('813',  '접대비',         'SGA', '800', TRUE,  8130, CURRENT_TIMESTAMP, 'SYSTEM'),
('814',  '통신비',         'SGA', '800', TRUE,  8140, CURRENT_TIMESTAMP, 'SYSTEM'),
('815',  '수도광열비',     'SGA', '800', TRUE,  8150, CURRENT_TIMESTAMP, 'SYSTEM'),
('817',  '세금과공과',     'SGA', '800', TRUE,  8170, CURRENT_TIMESTAMP, 'SYSTEM'),
('818',  '감가상각비',     'SGA', '800', TRUE,  8180, CURRENT_TIMESTAMP, 'SYSTEM'),
('819',  '임차료',         'SGA', '800', TRUE,  8190, CURRENT_TIMESTAMP, 'SYSTEM'),
('820',  '수선비',         'SGA', '800', TRUE,  8200, CURRENT_TIMESTAMP, 'SYSTEM'),
('821',  '보험료',         'SGA', '800', TRUE,  8210, CURRENT_TIMESTAMP, 'SYSTEM'),
('822',  '차량유지비',     'SGA', '800', TRUE,  8220, CURRENT_TIMESTAMP, 'SYSTEM'),
('826',  '도서인쇄비',     'SGA', '800', TRUE,  8260, CURRENT_TIMESTAMP, 'SYSTEM'),
('830',  '소모품비',       'SGA', '800', TRUE,  8300, CURRENT_TIMESTAMP, 'SYSTEM'),
('831',  '지급수수료',     'SGA', '800', TRUE,  8310, CURRENT_TIMESTAMP, 'SYSTEM'),
('833',  '광고선전비',     'SGA', '800', TRUE,  8330, CURRENT_TIMESTAMP, 'SYSTEM');

-- ===== 900 영업외손익 + 990 법인세비용 =====
INSERT INTO chart_of_accounts (code, name, category, parent_code, is_leaf, display_order, created_at, created_by) VALUES
('900',  '영업외손익',   'NON_OPERATING', NULL,  FALSE, 9000, CURRENT_TIMESTAMP, 'SYSTEM'),
('901',  '이자수익',     'NON_OPERATING', '900', TRUE,  9010, CURRENT_TIMESTAMP, 'SYSTEM'),
('904',  '임대료수익',   'NON_OPERATING', '900', TRUE,  9040, CURRENT_TIMESTAMP, 'SYSTEM'),
('906',  '잡이익',       'NON_OPERATING', '900', TRUE,  9060, CURRENT_TIMESTAMP, 'SYSTEM'),
('951',  '이자비용',     'NON_OPERATING', '900', TRUE,  9510, CURRENT_TIMESTAMP, 'SYSTEM'),
('952',  '외환차손',     'NON_OPERATING', '900', TRUE,  9520, CURRENT_TIMESTAMP, 'SYSTEM'),
('970',  '잡손실',       'NON_OPERATING', '900', TRUE,  9700, CURRENT_TIMESTAMP, 'SYSTEM'),
('991',  '법인세비용',   'INCOME_TAX',    NULL,  TRUE,  9910, CURRENT_TIMESTAMP, 'SYSTEM');
