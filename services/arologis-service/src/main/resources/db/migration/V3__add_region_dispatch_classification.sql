-- V3__add_region_dispatch_classification.sql
-- Phase 10 W10-1 PR-D Part 2-1 — REGION 가배차 지역별 분류
--
-- Samhan Public 프로그램에 native 이식 (노션 직접 통신 X — CSV 데이터 우리 DB 에 native 저장).
--
-- 1) region_dispatch_classifications — 지역 분류 마스터 (19+ 그룹)
--      group_name   = 사용자 노출 식별자 ("서울특별시", "경기동부", ...)
--      keywords     = 시군구 콤마 구분 검색어 ("송파구, 강남구, ...")
--      sort_order   = admin 화면 정렬용
--      활성 행 group_name unique
--
-- 2) vehicle_stops 보강 — classified_region_group 컬럼 추가
--      KakaoDispatchParser → RegionClassifier 통합 시 채움
--      parsed_partner_code 는 V1 에 BIGINT 로 이미 존재 (ix_vehicle_stops_partner_code_active)
--      → 본 마이그레이션은 classified_region_group 만 신규 추가
--
-- BaseEntity 7 audit fields 의무 (created_at / created_by / modified_at / modified_by /
--   deleted_at / deleted_by / is_deleted) — V1 패턴 1:1 일관

----------------------------------------------------------------------
-- 1) region_dispatch_classifications — 가배차 지역 분류 마스터
----------------------------------------------------------------------
CREATE TABLE region_dispatch_classifications (
    id              UUID            PRIMARY KEY,
    group_name      VARCHAR(50)     NOT NULL,
    keywords        TEXT            NOT NULL,
    sort_order      INT             NOT NULL DEFAULT 0,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 활성 행 group_name unique (Soft Delete 후 재등록 허용)
CREATE UNIQUE INDEX ux_region_classifications_group_active
    ON region_dispatch_classifications (group_name)
    WHERE is_deleted = FALSE;

-- sort_order 기반 admin 목록 조회 최적화
CREATE INDEX ix_region_classifications_sort_active
    ON region_dispatch_classifications (sort_order ASC, group_name ASC)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) vehicle_stops 보강 — classified_region_group
--    KakaoDispatchParser parse() 내부 RegionClassifier.classify(parsedAddress)
--    호출 결과 set. NULL 가능 (분류 매칭 실패 / 미해석 라인).
--    parsed_partner_code 는 V1 에서 이미 BIGINT 로 존재.
----------------------------------------------------------------------
ALTER TABLE vehicle_stops
    ADD COLUMN classified_region_group VARCHAR(50);

-- 지역별 통계 / 가배차 그룹 lookup 최적화 (NULL 제외)
CREATE INDEX ix_vehicle_stops_region_group_active
    ON vehicle_stops (classified_region_group)
    WHERE is_deleted = FALSE AND classified_region_group IS NOT NULL;
