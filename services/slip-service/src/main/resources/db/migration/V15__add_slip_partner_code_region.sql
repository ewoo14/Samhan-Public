-- V15__add_slip_partner_code_region.sql
-- Slip Service — PR-E1 BE-1 (slip-service 3 endpoint, GAS B 자동 조회 이식).
--
-- 컨텍스트:
--   * 출고전표 자동 조회 기반 GAS B 이식 — 다음날자 전표 이미지 / 전표 정리 리스트 / 날짜+거래처+지역 필터.
--   * legacy GAS = 이카운트 출고전표 엑셀 업로드. 본 PR = 우리 시스템 자체 자동 조회 → 이카운트 의존 0.
--   * 사용자 명시 (PR-D 회고): 모든 데이터 매핑은 partner_code source-of-truth.
--   * arologis-service vehicle_stops.classified_region_group 의 도메인 일관성 — slip 도 같은 분류 그룹명 보유.
--
-- 신규 컬럼 (slips):
--   1) partner_code      VARCHAR(50)  — 거래처코드 snapshot (사용자 노출 식별자).
--                          partner_id (UUID) 와 별도 — UUID 비공개 가드 의무 (memory feedback_uuid_no_user_visibility).
--                          PR-E1 의 BE-A0/A5/A6 query 직접 필터 (정확 일치).
--   2) classified_region_group VARCHAR(50) — 가배차 지역 그룹명 ("서울특별시" / "경기남부" 등).
--                          arologis-service RegionClassifier 가 결정한 값을 slip 생성/갱신 시 snapshot.
--                          본 PR 에서는 컬럼만 추가하고 채움 로직은 후속 슬라이스 (slip-service 가 RegionClassifier
--                          호출 또는 arologis-service Feign 으로 lookup) — 본 PR 는 query filter 만 활성.
--   3) driver_phone 인덱스 보강 — BE-A0 query 의 driverPhone like 필터 성능.
--
-- 컬럼 컨벤션:
--   * partner_code VARCHAR(50) — partner-service partners.partner_code 와 동일 길이 정책.
--   * classified_region_group VARCHAR(50) — arologis vehicle_stops.classified_region_group 동일.
--   * 둘 다 nullable — 기존 row backfill 없이 신규 데이터만 채움 (legacy 호환).
--
-- 회귀 영향:
--   * 신규 컬럼 nullable, 기존 IT 영향 0.
--   * 신규 인덱스 4종 (partner_code / region / 복합) — query 성능 + 기존 select 영향 0.

----------------------------------------------------------------------
-- 1) slips 신규 컬럼 2종
----------------------------------------------------------------------
ALTER TABLE slips
    ADD COLUMN partner_code             VARCHAR(50),
    ADD COLUMN classified_region_group  VARCHAR(50);

COMMENT ON COLUMN slips.partner_code IS
    'PR-E1 거래처코드 snapshot — partner-service partners.partner_code 와 동일. 사용자 노출 식별자 (UUID 비공개)';

COMMENT ON COLUMN slips.classified_region_group IS
    'PR-E1 가배차 지역 그룹명 snapshot — arologis-service RegionClassifier 산출값. 다음날자 전표 이미지 그룹핑';

----------------------------------------------------------------------
-- 2) BE-A0 query 성능용 인덱스 — slip_date + (partner_code | region | driver_phone)
----------------------------------------------------------------------

-- partner_code 활성행 인덱스 (날짜+거래처 dual filter 보강)
CREATE INDEX ix_slips_partner_code_date_active
    ON slips (partner_code, slip_date)
    WHERE is_deleted = FALSE AND partner_code IS NOT NULL;

-- region 활성행 인덱스 (다음날자 이미지 / 정리리스트 그룹핑)
CREATE INDEX ix_slips_region_date_active
    ON slips (classified_region_group, slip_date)
    WHERE is_deleted = FALSE AND classified_region_group IS NOT NULL;

-- driver_phone 활성행 인덱스 (BE-A0 driverPhone like 필터)
CREATE INDEX ix_slips_driver_phone_date_active
    ON slips (driver_phone, slip_date)
    WHERE is_deleted = FALSE AND driver_phone IS NOT NULL;
