-- V4__rename_parsed_partner_code.sql
-- Phase 10 PR-E 진입 전 선행 R2 — parsedPartnerCode 명칭 분리
--
-- 배경:
--   V1 의 vehicle_stops.parsed_partner_code (BIGINT) 는 "카톡 슬립번호" (예: 214) 식별자였으나,
--   partner-service 의 partner_code (String, "P-2026-0001" 비즈니스 식별자) 와 동일 명칭이라
--   PR-D 진행 과정에서 의미 혼동 위험이 제기됨 (D-P10-17 후속 backlog R2).
--
-- 정리:
--   1) 기존 BIGINT 컬럼을 parsed_kakao_seq 로 rename (의미 = 카톡 슬립번호)
--   2) 신규 VARCHAR 컬럼 parsed_partner_code 를 추가 (의미 = partner-service 의 partner_code)
--      - 본 PR 시점에는 NULL (lookup 미실행). PR-E1 (RegionClassifier + PartnerLookupClient lookup)
--        시점에 채움.
--   3) 기존 ix_vehicle_stops_partner_code_active 인덱스는 컬럼 rename 으로 자동 추적되며,
--      신규 String 컬럼용 별도 인덱스를 추가 (파셜 — NULL 제외).
--
-- BaseEntity 7 audit fields 의무 — 본 마이그레이션은 컬럼 rename + 신규 컬럼 추가만 수행
-- (audit 컬럼 영향 없음).

----------------------------------------------------------------------
-- 1) 기존 BIGINT 컬럼 rename — parsed_partner_code → parsed_kakao_seq
----------------------------------------------------------------------
ALTER TABLE vehicle_stops
    RENAME COLUMN parsed_partner_code TO parsed_kakao_seq;

-- V1 의 ix_vehicle_stops_partner_code_active 는 PostgreSQL 의 RENAME COLUMN 시
-- 컬럼 ref 가 자동 업데이트되지만, 인덱스명 자체에 partner_code 가 포함되어 있어
-- 가독성 회복을 위해 인덱스도 rename.
ALTER INDEX ix_vehicle_stops_partner_code_active
    RENAME TO ix_vehicle_stops_kakao_seq_active;

----------------------------------------------------------------------
-- 2) 신규 String 컬럼 parsed_partner_code (partner-service partner_code lookup 결과)
--    - PR-E1 (RegionClassifier + PartnerLookupClient lookup) 시점에 채움
--    - 본 PR 단독으로는 모든 row 가 NULL (lookup 미실행, fail-soft)
----------------------------------------------------------------------
ALTER TABLE vehicle_stops
    ADD COLUMN parsed_partner_code VARCHAR(50);

-- partner_code 기반 lookup 최적화 (파셜 — NULL 제외, 활성 행만)
CREATE INDEX ix_vehicle_stops_partner_code_active
    ON vehicle_stops (parsed_partner_code)
    WHERE is_deleted = FALSE AND parsed_partner_code IS NOT NULL;
