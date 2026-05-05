-- V4__seed_spec_key_template.sql
-- SpecKeyTemplate 53 row 시드 — DOMAIN-EXTENSIONS §4 매트릭스 + Migration Plan §2.1.1.2.
-- 출처: estimate Code.js getSpecDetailMap_() (line 1006-1364) — scanHome / scanSingle / scanComm
--       의 idx(H, [...]) 호출 인자 매트릭스 그대로 채택.
--
-- 카테고리별 row 수: HOME_MULTI 14 + SINGLE_SET 21 + COMMERCIAL_MULTI 16 + LEGACY 2 = 53
-- (OTHER 0 = 사용자 자유 입력)

-- HOME_MULTI 14 row (배관경, 냉매가스, 차단기, 전원선, 제품크기, 제품중량, 포장치수, 포장중량,
--                   최대장배관, 최대고저차, 에너지소비효율등급, 냉방성능Kcal/h, 냉방성능kW, 소비전력)
INSERT INTO spec_key_template (id, estimate_category, spec_key, default_unit, display_order, is_recommended,
                                created_at, created_by, is_deleted) VALUES
 ('11111111-0000-0000-0000-000000000101', 'HOME_MULTI', '배관경',           NULL,    1,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000102', 'HOME_MULTI', '냉매가스',         NULL,    2,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000103', 'HOME_MULTI', '차단기',           'A',     3,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000104', 'HOME_MULTI', '전원선',           'mm²',   4,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000105', 'HOME_MULTI', '제품크기',         'mm',    5,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000106', 'HOME_MULTI', '제품중량',         'kg',    6,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000107', 'HOME_MULTI', '포장치수',         'mm',    7,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000108', 'HOME_MULTI', '포장중량',         'kg',    8,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000109', 'HOME_MULTI', '최대장배관',       'm',     9,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000110', 'HOME_MULTI', '최대고저차',       'm',    10,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000111', 'HOME_MULTI', '에너지소비효율등급', NULL,  11,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000112', 'HOME_MULTI', '냉방성능(Kcal/h)', 'Kcal/h', 12, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000113', 'HOME_MULTI', '냉방성능(kW)',     'kW',   13,  TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000114', 'HOME_MULTI', '소비전력(정격)',   'kW',   14,  TRUE, NOW(), 'system', FALSE);

-- SINGLE_SET 21 row (등급(냉방/난방), 배관경, 냉매가스, 냉방성능Kcal/h+kW, 난방성능Kcal/h+kW,
--                   소비전력(cool/heat 분리), 전원, 차단기, 실내/실외 크기/중량/포장/포장중량,
--                   배관길이, 고낙차)
INSERT INTO spec_key_template (id, estimate_category, spec_key, default_unit, display_order, is_recommended,
                                created_at, created_by, is_deleted) VALUES
 ('11111111-0000-0000-0000-000000000201', 'SINGLE_SET', '등급(냉방/난방)',     NULL,    1, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000202', 'SINGLE_SET', '배관경',              NULL,    2, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000203', 'SINGLE_SET', '냉매가스',            NULL,    3, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000204', 'SINGLE_SET', '냉방성능(Kcal/h)',    'Kcal/h', 4, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000205', 'SINGLE_SET', '냉방성능(kW)',        'kW',    5, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000206', 'SINGLE_SET', '난방성능(Kcal/h)',    'Kcal/h', 6, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000207', 'SINGLE_SET', '난방성능(kW)',        'kW',    7, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000208', 'SINGLE_SET', '소비전력(냉방)',      'kW',    8, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000209', 'SINGLE_SET', '소비전력(난방)',      'kW',    9, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000210', 'SINGLE_SET', '전원',                'mm²',  10, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000211', 'SINGLE_SET', '차단기',              'A',    11, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000212', 'SINGLE_SET', '실내기크기',          'mm',   12, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000213', 'SINGLE_SET', '실외기크기',          'mm',   13, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000214', 'SINGLE_SET', '실내기중량',          'kg',   14, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000215', 'SINGLE_SET', '실외기중량',          'kg',   15, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000216', 'SINGLE_SET', '실내기포장',          'mm',   16, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000217', 'SINGLE_SET', '실외기포장',          'mm',   17, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000218', 'SINGLE_SET', '실내기포장중량',      'kg',   18, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000219', 'SINGLE_SET', '실외기포장중량',      'kg',   19, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000220', 'SINGLE_SET', '배관길이',            'm',    20, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000221', 'SINGLE_SET', '고낙차',              'm',    21, TRUE, NOW(), 'system', FALSE);

-- COMMERCIAL_MULTI 16 row (HOME_MULTI 14 + 난방성능Kcal/h + 난방성능kW + 덕트구경)
INSERT INTO spec_key_template (id, estimate_category, spec_key, default_unit, display_order, is_recommended,
                                created_at, created_by, is_deleted) VALUES
 ('11111111-0000-0000-0000-000000000301', 'COMMERCIAL_MULTI', '배관경',           NULL,    1, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000302', 'COMMERCIAL_MULTI', '냉매가스',         NULL,    2, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000303', 'COMMERCIAL_MULTI', '차단기',           'A',     3, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000304', 'COMMERCIAL_MULTI', '전원선',           'mm²',   4, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000305', 'COMMERCIAL_MULTI', '제품크기',         'mm',    5, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000306', 'COMMERCIAL_MULTI', '제품중량',         'kg',    6, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000307', 'COMMERCIAL_MULTI', '포장치수',         'mm',    7, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000308', 'COMMERCIAL_MULTI', '포장중량',         'kg',    8, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000309', 'COMMERCIAL_MULTI', '최대장배관',       'm',     9, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000310', 'COMMERCIAL_MULTI', '최대고저차',       'm',    10, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000311', 'COMMERCIAL_MULTI', '에너지소비효율등급', NULL,  11, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000312', 'COMMERCIAL_MULTI', '냉방성능(Kcal/h)', 'Kcal/h', 12, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000313', 'COMMERCIAL_MULTI', '냉방성능(kW)',     'kW',   13, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000314', 'COMMERCIAL_MULTI', '소비전력(정격)',   'kW',   14, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000315', 'COMMERCIAL_MULTI', '난방성능(Kcal/h)', 'Kcal/h', 15, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000316', 'COMMERCIAL_MULTI', '덕트구경',         'mm',   16, TRUE, NOW(), 'system', FALSE);

-- LEGACY 2 row (구형 — 규격, 비고)
INSERT INTO spec_key_template (id, estimate_category, spec_key, default_unit, display_order, is_recommended,
                                created_at, created_by, is_deleted) VALUES
 ('11111111-0000-0000-0000-000000000401', 'LEGACY', '규격', NULL, 1, TRUE, NOW(), 'system', FALSE),
 ('11111111-0000-0000-0000-000000000402', 'LEGACY', '비고', NULL, 2, TRUE, NOW(), 'system', FALSE);

-- 합계: HOME_MULTI 14 + SINGLE_SET 21 + COMMERCIAL_MULTI 16 + LEGACY 2 = 53 row.
-- plan §2.1.1.2 의 53 row 일치 (DECISIONS G19 검증 기준).
