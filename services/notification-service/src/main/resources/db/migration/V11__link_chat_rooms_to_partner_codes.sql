-- V11__link_chat_rooms_to_partner_codes.sql
-- #1224 — 공유 DB 직접 변경 대신 Flyway로 재현 가능한 단톡방 거래처코드 연결.
-- 데이터 기준: 2026-08-15 READ ONLY 조회 결과 112행.
-- 연결 106 / 모호 4 / 미매칭 2. partner-service 마스터는 변경하지 않는다.

BEGIN;

ALTER TABLE partner_chat_room_mappings
    ADD COLUMN partner_link_status VARCHAR(32) NOT NULL DEFAULT 'UNLINKED',
    ADD COLUMN partner_link_reason VARCHAR(255);

CREATE TABLE partner_chat_room_mapping_link_audit (
    id BIGSERIAL PRIMARY KEY,
    link_batch VARCHAR(50) NOT NULL,
    mapping_id UUID NOT NULL,
    prior_partner_code VARCHAR(50) NOT NULL,
    new_partner_code VARCHAR(50),
    source_name VARCHAR(200) NOT NULL,
    chat_room_name VARCHAR(200) NOT NULL,
    matched_partner_name VARCHAR(200),
    match_method VARCHAR(100) NOT NULL,
    candidate_count INTEGER NOT NULL,
    decision_status VARCHAR(32) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by VARCHAR(100) NOT NULL,
    CONSTRAINT ux_chat_room_link_audit_batch_mapping UNIQUE (link_batch, mapping_id)
 );

CREATE TEMP TABLE _1224_chat_room_link_candidates (
    prior_partner_code VARCHAR(50) NOT NULL, source_name VARCHAR(200) NOT NULL,
    chat_room_name VARCHAR(200) NOT NULL, new_partner_code VARCHAR(50),
    matched_partner_name VARCHAR(200), match_method VARCHAR(100) NOT NULL,
    decision_status VARCHAR(32) NOT NULL, candidate_count INTEGER NOT NULL
 ) ON COMMIT DROP;

INSERT INTO _1224_chat_room_link_candidates (prior_partner_code, source_name, chat_room_name, new_partner_code, matched_partner_name, match_method, decision_status, candidate_count) VALUES
  ('LEGACY-NAME-095571bc7314', '경인공조-황인선님', '경인공조 발주방', '1370441956', '경인공조-황인선님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-db74bdd32ed8', '광주연합에어컨(나수정)', '광주연합에어컨 발주방', '3291602458', '광주연합에어컨(나수정)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-1518309b912f', '구)주식회사 그레이프시스템(휴먼넷)', '그레이프(휴먼넷) 발주방', '6708701231', '♣구)주식회사 그레이프시스템(휴먼넷)', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-c02f9b8ffe16', '신규-주식회사 그레이프(휴먼넷)', '그레이프(휴먼넷) 발주방', '5318703012', '신규-주식회사 그레이프(휴먼넷)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-ba98ecb9b3a3', '그린공조시스템 주식회사(조영진)', '그린시스템에어컨 발주방', '8038802176', '폐업) 그린공조시스템 주식회사(조영진)', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-acf1c36f8e5a', '다온공조-유근호외 1명', '그린시스템에어컨 발주방', '3150838705', '다온공조-유근호외 1명', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0303c9eddbbc', '미소공조(박정호)-그린공조시스템', '그린시스템에어컨 발주방', '1263025085', '미소공조(박정호)-그린공조시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-58749cd83ac4', '정도엔지니어링-이경진대표(그린공조시스템-조영진대표 출고건)', '그린시스템에어컨 발주방', '1211746387', '정도엔지니어링-이경진대표(그린공조시스템-조영진대표 출고건)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-bb0f694398fa', '뉴에어시스템에어컨(한현배)', '뉴에어시스템 발주방', '2291465974', '뉴에어시스템에어컨(한현배)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-7647376aa5b4', '니아이엔지-오승한', '니아이엔지 발주방', '3735700412', '♣사용x) 니아이엔지-오승한', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-91864b304663', '사용x) 니아이엔지-오승한', '니아이엔지 발주방', '3735700412', '♣사용x) 니아이엔지-오승한', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-191a9c61b6af', '다인공조시스템 주식회사-이혜진', '다인공조 발주방', '1168803300', '다인공조시스템 주식회사-이혜진', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-01f98fab12d7', '주식회사 다함', '다함 발주방', '6958601117', '주식회사 다함', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-9231784ec6d0', '주식회사 더라인(최종진)', '더라인 발주방', '8478601377', '주식회사 더라인(최종진)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f1b3edf42caf', '(주)삼성에스에이씨비투비(더블유케이)', '더블유케이 발주방', '2188601069', '(주)삼성에스에이씨비투비(더블유케이)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-fb39e7ff52f3', '주식회사 도원시스템', '도원시스템 발주방', '6808101162', '주식회사 도원시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-59f877b819a9', '동해공조시스템에어컨-손선아', '동해공조 발주방', '4801902322', '동해공조시스템에어컨-손선아', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-06b61d63cfd0', '디지털프라자 (주) 두정점 (김은경)', '두정점 발주방', '3128161229', '디지털프라자 (주) 두정점 (김은경)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-cd88c732b2b1', '동우공조-배영수님', '디에스솔루션즈 발주방', '7797400558', '동우공조-배영수님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-fe0fa77f1eed', '주식회사 디에스솔루션즈(이효정)', '디에스솔루션즈 발주방', '6628600549', '주식회사 디에스솔루션즈(이효정)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-77ff68299c1c', '캐리어MRO', '디에스솔루션즈 발주방', '3111065289', '캐리어MRO', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f6d1d035a792', '디와이 시스템에어컨 - 한동현님', '디와이시스템에어컨 발주방', '6800403422', '디와이 시스템에어컨 - 한동현님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-e21d8ea06d9b', '랜드유통(최경호)', '랜드유통 발주방', '1060818309', '랜드유통(최경호)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f810a795013c', '리더스 비투비(B2B)', '리더스비투비 발주방', '5201900675', '리더스비투비(B2B)-진승지', '정규화 후 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-00b9f16589d0', '케이에스B2B(김태영)', '리더스비투비 발주방', '5331600573', '케이에스B2B(김태영)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-80928ae20af3', '메인공조(김윤)', '메인공조 발주방', '2063143353', '메인공조(김윤)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-d7ade1c8a92a', '메타공조시스템-이은지', '메타공조시스템 발주방', '7033301372', '메타공조시스템-이은지', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0c451f00e911', '명성유통(조희선)', '명성유통 발주방', '5042231142', '명성유통(조희선)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0bb1e579fb69', '우주공조시스템-조석현', '명성유통 발주방', '5041369971', '우주공조시스템-조석현', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-fb3306b732ac', '바른시스템에어컨(배가율)', '바른시스템 발주방', '6212700842', '바른시스템에어컨(배가율)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-5399ca025664', '(주)사계절솔루션(염은희)', '사계절솔루션 발주방', NULL, NULL, '후보 ', 'UNLINKED_AMBIGUOUS', 12),
  ('LEGACY-NAME-41dac75d7e38', '(주)총알설치-염은희(사계절솔루션)', '사계절솔루션 발주방', '8238603229', '(주)총알설치-염은희(사계절솔루션)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-7ff0999ddbcf', '다온테크(염명희)-사계절솔루션(염은희)', '사계절솔루션 발주방', '8840202415', '다온테크(염명희)-사계절솔루션(염은희)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-59cb165ca1c0', '썸머프레시(이정수)-사계절솔루션 주문건', '사계절솔루션 발주방', '8250702712', '썸머프레시(이정수)-사계절솔루션 주문건', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-8b6ebc5774f1', '씨엘(CL)공조-최정현', '사계절솔루션 발주방', '6534400773', '씨엘(CL)공조-최정현', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-65860803ba9a', '에어컨요기요(고명준)-사계절솔루션', '사계절솔루션 발주방', '8031002672', '에어컨요기요(고명준)-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0930354d121a', '에어컨프라자(전경인)-사계절솔루션', '사계절솔루션 발주방', '3025300858', '에어컨프라자(전경인)-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-1a52a956c258', '여기시스템-사계절솔루션', '사계절솔루션 발주방', '8630802759', '여기시스템-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0dd4f7fd2b3f', '올드시스템(소성우)-사계절솔루션', '사계절솔루션 발주방', '4590502748', '올드시스템(소성우)-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-4acd03623cc1', '초롱꽃에어컨(정은혜)-사계절솔루션', '사계절솔루션 발주방', '3173201193', '초롱꽃에어컨(정은혜)-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-b29a2fd190a3', '쿨핫남(송경인)-사계절솔루션', '사계절솔루션 발주방', '8271701741', '쿨핫남(송경인)-사계절솔루션', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-9b06909c4877', '퍼스트공조 (사계절솔루션-이승준 타사업자)', '사계절솔루션 발주방', '6904800635', '퍼스트공조 (사계절솔루션-이승준 타사업자)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-bfe9ac609b1a', '하늘시스템(이승준)-(주)사계절솔루션(염은희)', '사계절솔루션 발주방', '8811902020', '하늘시스템(이승준)-(주)사계절솔루션(염은희)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-fbbcd6021cee', '(주)사온공조시스템', '사온공조 발주방', '7098602166', '(주)사온공조시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-34c7f97a8e91', '삼공주에어컨-최성진님', '삼공주에어컨 발주방', '4941002764', '삼공주에어컨-최성진님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-1edec1b9cc47', '주식회사 삼성스토어고성', '삼성스토어 고성 발주방', '6128146564', '주식회사 삼성스토어고성', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-dfb54ceff829', '(주)더블유케이시스템(정성훈)', '삼성에스에이씨(더블유케이) 발주방', '3158601962', '(주)더블유케이시스템(정성훈)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-5070e6928e5b', '상현공조(박마리아)', '상현공조 발주방', '1322881809', '상현공조-박마리아', '정규화 후 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-113a73f91687', '새롬ES(송진섭)', '새롬이에스 발주방', '7702500719', '새롬ES(송진섭)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-5ff9c529a0d8', '주식회사새롬이에스', '새롬이에스 발주방', '1978701449', '주식회사새롬이에스', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-fc86ea6d9fcb', '서희공조(김한구)', '서희공조 발주방', '1390386404', '*서희공조(김한구)', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-68d8740732bd', '주식회사 세원공조시스템(김대식)', '세원공조시스템 발주방', '7968102976', '주식회사 세원공조시스템(김대식)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-a334eaca5aed', '수공조시스템(안성열)', '수공조 발주방', '1410586814', '수공조시스템(안성열)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-28bc528fdd9e', '스피드공조-유용선', '스피드공조 발주방', '4021255140', '스피드공조-유용선', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-1f54eaa395db', '주식회사 한결시스템', '신한결시스템발주방', NULL, NULL, '후보 ', 'UNLINKED_AMBIGUOUS', 5),
  ('LEGACY-NAME-bc9794f94750', '씨앤씨공조 주식회사(임현섭)-하트쿨엔지니어링', '씨앤씨 발주방', '5028642233', '씨앤씨공조 주식회사(임현섭)-하트쿨엔지니어링', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-c5d4cf70a516', '(주)씨에스비전(양호철)', '씨에스비전 발주방', '1138667752', '(주)씨에스비전(양호철)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-6b092a0d40b4', '(주)아이에스공조', '아이에스공조 발주방', '6278802698', '(주)아이에스공조', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-6be3da0b333d', '(주)아이티글로벌', '아이티글로벌 발주방', '8848101425', '(주)아이티글로벌', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f83bfb4af56c', '(주)아이티글로벌그룹-김영숙', '아이티글로벌 발주방', '5338802845', '(주)아이티글로벌그룹-김영숙', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-d64ed404192b', '안스에어컨(안국진)', '안스에어컨 발주방', '1283934846', '안스에어컨(안국진)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-60ab9277a60e', '양산물류총판(최은영)', '양산물류 발주방', '6323101362', '양산물류총판(최은영)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-d9f50902823f', '주식회사 에스씨이엔지', '에스씨이엔지 발주방', '2218135880', '주식회사 에스씨이엔지', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-2663018680c1', '(주)에스에이솔루텍(한정훈)', '에스에이솔루텍 발주방', '4758802006', '(주)에스에이솔루텍(한정훈)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-43eefdcefbb1', '미래정보시스템주식회사(조철운)-에스에이솔루텍', '에스에이솔루텍 발주방', '6068199542', '미래정보시스템주식회사(조철운)-에스에이솔루텍', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-cb9abaa54ee7', '공기를디자인하는사람들 주식회사', '에어디자이너(구 지에스) 발주방', '6508103591', '공기를디자인하는사람들 주식회사', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-7013253b0295', '공기를디자인하는사람들 주식회사-최상비', '에어디자이너(구 지에스) 발주방', NULL, NULL, '후보 없음', 'UNLINKED_UNMATCHED', 0),
  ('LEGACY-NAME-a58d5432cb18', '에어디자이너 주식회사', '에어디자이너(구 지에스) 발주방', '6568702893', '에어디자이너 주식회사', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-46c8ac6bbdb1', '에어디자이너 주식회사-최상비 대표', '에어디자이너(구 지에스) 발주방', NULL, NULL, '후보 없음', 'UNLINKED_UNMATCHED', 0),
  ('LEGACY-NAME-bf43d5436054', '(주)에어컨설팅-정명수', '에어컨설팅 발주방', '5498103495', '(주)에어컨설팅-정명수', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-7b83c4cfa5ee', '주식회사 에어트', '에어트 발주방', '8728102630', '주식회사 에어트', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-12b083e7f89c', '에이아이공조시스템-차영기', '에이아이공조 발주방', '6241600961', '에이아이공조시스템-차영기', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-0b35613beba8', '에이앤더블유 주식회사(박무경, 최영기)', '에이앤더블유 회계방', '2068709293', '에이앤더블유 주식회사(박무경, 최영기)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-2962cb5007c7', '유한회사 에이앤에이공조시스템-조용운대표님', '에이앤에이 발주방', '7788802185', '유한회사 에이앤에이공조시스템-조용운대표님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-848926c38af5', '(주)엘케이토탈공조', '엘케이토탈공조 발주방', '1148666339', '(주)엘케이토탈공조', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-25008756ecf3', '(주)영에어시스템(권혜영)-법인사업자', '영에어 발주방', '1588802571', '(주)영에어시스템(권혜영)-법인사업자', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f95051bd83d5', '주식회사 예스특판(이주영)', '예스특판 발주방', '8718100468', '주식회사 예스특판(이주영)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-b31db21826b4', '주식회사 예원시스템(박봉기)', '예원시스템 발주방', '4368601987', '주식회사 예원시스템(박봉기)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-8fbdae8f7f07', '주식회사 제이앤피공조', '오성공조 발주방', '2568700899', '주식회사 제이앤피공조', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-a700490c1c78', '주식회사오성공조', '오성공조 발주방', '7608701081', '♣주식회사오성공조', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-9d0b99114e5a', '온누리공조(주)-이재호', '온누리공조 발주방', '6818102844', '온누리공조(주)-이재호', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-3c26eb92c77e', '주식회사 윌리-정현수', '윌리 발주방', '4058115046', '주식회사 윌리-정현수', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-87f75d32c61c', '윤학에어컨-김화경님', '윤학에어컨', '2230794115', '윤학에어컨-김화경님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-2576624ebe05', '(주)은한시스템(장나영)', '은한시스템 발주방', '1168602457', '(주)은한시스템(장나영)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-e8c41bff180b', '네오시스템(구 코운시스템)-박현환', '은한시스템 발주방', '1192200102', '네오시스템(구 코운시스템)-박현환', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-1b5344e72ace', '더시원공조시스템', '은한시스템 발주방', '4340902242', '더시원공조시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-77f659b83ddc', '우빈공조(장현식)', '은한시스템 발주방', '7340200223', '우빈공조(장현식)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-153182de8ac5', '이가건축(이미연)-임마누엘', '임마누엘 발주방', '5743300815', '이가건축(이미연)-임마누엘', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-f40a807fcf74', '임마누엘 시스템 에어컨(임현희)', '임마누엘 발주방', '4193100998', '♣임마누엘 시스템 에어컨(임현희)', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-1e439784735a', '주식회사 제이시스템', '제이시스템 발주방', '8428102605', '주식회사 제이시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-cc18853cc78e', '*(주)제트공조설계사무소-손인우', '제트공조 발주방', '8148602755', '*(주)제트공조설계사무소-손인우', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-6f50c42f4894', '준공조-김준성대표님(구,와이케이공조)', '준공조 발주방', '1110854627', '준공조-김준성대표님(구,와이케이공조)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-62d03bbb3e45', '지엘아트시스템 주식회사', '지엘아트시스템 발주방', '1208822418', '지엘아트시스템 주식회사', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-dd455d82e342', '진에어시스템(황재문)', '진에어시스템 발주방', '2232300204', '진에어시스템(황재문)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-59b2dd57f463', '컴온 에어컨', '컴온에어컨 발주방', '8821900966', '컴온에어컨', '정규화 후 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-59fd43bb6e52', '컴온에어컨', '컴온에어컨 발주방', '8821900966', '컴온에어컨', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-da6add13aecb', '케이지시스템(강구홍)', '케이지 발주방', '2360902255', '♣케이지시스템(강구홍)', '이름 검색 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-bedec819fd7c', '(주)큐비코이앤씨 - 조일상님', '큐비코 발주방', '3998102101', '(주)큐비코이앤씨 - 조일상님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-23edee114cfd', '주식회사 탑시스템이엔지-임진환', '탑시스템이엔지 발주방', '2948603299', '주식회사 탑시스템이엔지-임진환', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-7c5317d40693', '티엠에쓰(TMS)엔지니어링(강성구)', '티엠에쓰엔지니어링 발주방', '1351828328', '티엠에쓰(TMS)엔지니어링(강성구)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-500ec32eacd7', '퍼스트공조-박상준님', '퍼스트공조 발주방', '6832001665', '퍼스트공조-박상준님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-ef0addb45d29', '편한공조시스템', '편한공조 발주방', '7952800437', '편한공조시스템', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-326ac6cc2438', '(주)하늘금-박동수', '하늘금 발주방', '2098123671', '(주)하늘금-박동수', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-33486c83b3c7', '주식회사 하랑공조 - 권규훈님', '하랑공조 발주방', '3198802666', '주식회사 하랑공조 - 권규훈님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-9e63f4b00c61', '주식회사한결시스템-신윤한', '한결시스템 발주방', '5688801217', '주식회사 한결시스템-신윤한', '정규화 후 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-439ee2ceeb21', '주식회사 한성시스템에어컨', '한성시스템에어컨 발주방', NULL, NULL, '후보 ', 'UNLINKED_AMBIGUOUS', 3),
  ('LEGACY-NAME-93c4d7b1e73f', '주식회사 한성시스템에어컨(박혜린)', '한성시스템에어컨 발주방', NULL, NULL, '후보 ', 'UNLINKED_AMBIGUOUS', 3),
  ('LEGACY-NAME-1f706cc21a49', '스마트공조시스템(박현후)', '환경시스템 발주방', '2544000812', '스마트공조시스템(박현후)', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-97097c608a74', '키키에어컨 - 권대현님', '환경시스템 발주방', '4984300747', '키키에어컨 - 권대현님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-c851d5289d40', '환경시스템공조 - 김진혁대표님', '환경시스템 발주방', '1117100334', '환경시스템공조-김진혁대표님', '정규화 후 단일 후보', 'LINKED', 1),
  ('LEGACY-NAME-9072d1f68f5a', '환경시스템공조-김진혁대표님', '환경시스템공조 발주방', '1117100334', '환경시스템공조-김진혁대표님', '정확 일치', 'LINKED', 1),
  ('LEGACY-NAME-3797555281db', '흑풍시스템-홍대기', '흑풍시스템 발주방', '1221050231', '흑풍시스템-홍대기', '정확 일치', 'LINKED', 1);

INSERT INTO partner_chat_room_mapping_link_audit (link_batch, mapping_id, prior_partner_code, new_partner_code, source_name, chat_room_name, matched_partner_name, match_method, candidate_count, decision_status, applied_by)
SELECT '1224-20260815',m.id,m.partner_code,c.new_partner_code,m.partner_business_name_snapshot,m.chat_room_name,c.matched_partner_name,c.match_method,c.candidate_count,c.decision_status,'migration:V11'
FROM partner_chat_room_mappings m JOIN _1224_chat_room_link_candidates c ON c.prior_partner_code=m.partner_code AND c.source_name=m.partner_business_name_snapshot AND c.chat_room_name=m.chat_room_name WHERE m.is_deleted=FALSE;

DROP INDEX ux_chat_mapping_partner_room_active;

UPDATE partner_chat_room_mappings m SET partner_code=c.new_partner_code,partner_link_status=c.decision_status,partner_link_reason=c.match_method,modified_at=CURRENT_TIMESTAMP,modified_by='migration:V11' FROM _1224_chat_room_link_candidates c WHERE m.is_deleted=FALSE AND c.prior_partner_code=m.partner_code AND c.source_name=m.partner_business_name_snapshot AND c.chat_room_name=m.chat_room_name AND c.decision_status='LINKED';

UPDATE partner_chat_room_mappings m SET partner_link_status=c.decision_status,partner_link_reason=c.match_method,modified_at=CURRENT_TIMESTAMP,modified_by='migration:V11' FROM _1224_chat_room_link_candidates c WHERE m.is_deleted=FALSE AND c.prior_partner_code=m.partner_code AND c.source_name=m.partner_business_name_snapshot AND c.chat_room_name=m.chat_room_name AND c.decision_status<>'LINKED';

CREATE UNIQUE INDEX ux_chat_mapping_partner_room_active ON partner_chat_room_mappings (partner_code,chat_room_name,partner_business_name_snapshot) WHERE is_deleted=FALSE;

COMMIT;

-- ROLLBACK: BEGIN; DROP INDEX ux_chat_mapping_partner_room_active;
-- UPDATE partner_chat_room_mappings m SET partner_code=a.prior_partner_code,partner_link_status='UNLINKED',partner_link_reason=NULL,modified_at=CURRENT_TIMESTAMP,modified_by='rollback:V11' FROM partner_chat_room_mapping_link_audit a WHERE a.link_batch='1224-20260815' AND a.mapping_id=m.id;
-- CREATE UNIQUE INDEX ux_chat_mapping_partner_room_active ON partner_chat_room_mappings (partner_code,chat_room_name) WHERE is_deleted=FALSE;
-- ALTER TABLE partner_chat_room_mappings DROP COLUMN partner_link_status,DROP COLUMN partner_link_reason;
-- DROP TABLE partner_chat_room_mapping_link_audit; COMMIT;
