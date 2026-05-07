-- V1__init_arologis.sql
-- Phase 10 W10-1 arologis-service — initial schema (5 entity + 1 GPS 추적 + ShedLock).
-- BaseEntity audit columns mirror partner-service / groupware-service / notification-service / dashboard-service V1 정확히.
-- Soft-delete 는 application-side @SQLRestriction("is_deleted = false") 로 강제.
--
-- 컬럼 타입 컨벤션 (Phase 11 AWS RDS cutover 호환):
--   * 짧은 문자열은 VARCHAR(N) (CHAR/bpchar 금지)
--   * GPS 위도/경도는 NUMERIC(10, 7) (약 1.1cm 정확도)
--   * 시간은 TIMESTAMP (Hibernate JPA LocalDateTime 매핑)
--   * 날짜는 DATE (Hibernate JPA LocalDate 매핑)
--   * Postgres standard SQL only — RDS PostgreSQL 16 호환

----------------------------------------------------------------------
-- 1) dispatches — 배차 1건 (카톡 1 메시지)
--    dispatch_date    = 도착 일자 (카톡 헤더 "8일착")
--    dispatch_type    = DAY / NIGHT / EXPRESS
--    raw_kakao_text   = 카톡 원본 메시지 (audit 용, TEXT)
----------------------------------------------------------------------
CREATE TABLE dispatches (
    id              UUID            PRIMARY KEY,
    dispatch_date   DATE            NOT NULL,
    dispatch_type   VARCHAR(20)     NOT NULL,
    raw_kakao_text  TEXT,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

-- 날짜 + 유형별 lookup (admin dashboard)
CREATE INDEX ix_dispatches_date_type_active
    ON dispatches (dispatch_date DESC, dispatch_type)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 2) vehicles — 차량 1대 (카톡 "1." "2." 그룹)
--    (dispatch_id, sequence) 활성 행 unique
--    label                = 카톡 헤더 옆 텍스트 (예: "상일+초월")
--    assigned_driver_id   = 매칭 완료 후 set (UUID — driverCode 응답 변환 의무)
--    match_source         = 매칭 경로 (INTERNAL_APP / EXTERNAL_INSUNG_QUICK / ...)
--    external_ref_id      = 외부 vendor 주문번호
--    status               = PENDING / MATCHING / ASSIGNED / DEPARTED / DELIVERED / CANCELLED
----------------------------------------------------------------------
CREATE TABLE vehicles (
    id                  UUID            PRIMARY KEY,
    dispatch_id         UUID            NOT NULL REFERENCES dispatches(id),
    sequence            INT             NOT NULL,
    tonnage             VARCHAR(20)     NOT NULL,
    label               VARCHAR(200),
    assigned_driver_id  UUID,
    match_source        VARCHAR(30),
    external_ref_id     VARCHAR(100),
    status              VARCHAR(20)     NOT NULL,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_vehicles_dispatch_seq_active
    ON vehicles (dispatch_id, sequence)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_vehicles_dispatch_status_active
    ON vehicles (dispatch_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_vehicles_assigned_driver_active
    ON vehicles (assigned_driver_id)
    WHERE is_deleted = FALSE AND assigned_driver_id IS NOT NULL;

----------------------------------------------------------------------
-- 3) vehicle_stops — 정차 1건 (카톡 라인)
--    (vehicle_id, sequence) 활성 행 unique
--    raw_text             = 카톡 원본 라인 (TEXT)
--    parsed_address       = 파싱된 주소 (옵션, 미해석 시 null)
--    parsed_partner_name  = 사업자명 (예: "에스엠하나공조")
--    parsed_partner_code  = 전표번호 BIGINT (예: 214)
--    notes                = 특이사항 ("9시하차" / "오전일찍" 등)
--    status               = PENDING / ARRIVED / DELIVERED / FAILED / UNPARSED
----------------------------------------------------------------------
CREATE TABLE vehicle_stops (
    id                      UUID            PRIMARY KEY,
    vehicle_id              UUID            NOT NULL REFERENCES vehicles(id),
    sequence                INT             NOT NULL,
    raw_text                TEXT            NOT NULL,
    parsed_address          VARCHAR(500),
    parsed_partner_name     VARCHAR(200),
    parsed_partner_code     BIGINT,
    notes                   TEXT,
    status                  VARCHAR(20)     NOT NULL,
    actual_arrival_time     TIMESTAMP,
    actual_delivery_time    TIMESTAMP,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_vehicle_stops_vehicle_seq_active
    ON vehicle_stops (vehicle_id, sequence)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_vehicle_stops_partner_code_active
    ON vehicle_stops (parsed_partner_code)
    WHERE is_deleted = FALSE AND parsed_partner_code IS NOT NULL;

----------------------------------------------------------------------
-- 4) drivers — 배송기사 (외부 vendor 매칭 또는 본 어플 사용자)
--    driver_code      = 사용자 노출 식별자 (활성 행 unique)
--    phone_number     = 활성 행 unique
--    source           = INTERNAL / EXTERNAL_INSUNG_QUICK / EXTERNAL_SMS / EXTERNAL_KAKAO / MANUAL
--    app_installed    = 본 어플 설치 여부
--    app_user_id      = INTERNAL 시 user-service userId (옵션)
----------------------------------------------------------------------
CREATE TABLE drivers (
    id              UUID            PRIMARY KEY,
    driver_code     VARCHAR(50)     NOT NULL,
    phone_number    VARCHAR(20)     NOT NULL,
    vehicle_type    VARCHAR(20),
    source          VARCHAR(30)     NOT NULL,
    app_installed   BOOLEAN         NOT NULL DEFAULT FALSE,
    app_user_id     UUID,

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_drivers_code_active
    ON drivers (driver_code)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX ux_drivers_phone_active
    ON drivers (phone_number)
    WHERE is_deleted = FALSE;

CREATE INDEX ix_drivers_source_app_active
    ON drivers (source, app_installed)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 5) signatures — 전자서명 (slip-service 통합 W10-4)
--    source       = LINK / APP
--    image_ref    = 이미지 reference (file-server 경로, W10-4)
--    GPS          = NUMERIC(10,7) — 약 1.1cm 정확도 (APP 일 때만)
----------------------------------------------------------------------
CREATE TABLE signatures (
    id                  UUID            PRIMARY KEY,
    stop_id             UUID            NOT NULL REFERENCES vehicle_stops(id),
    source              VARCHAR(20)     NOT NULL,
    image_ref           VARCHAR(500),
    captured_at         TIMESTAMP       NOT NULL,
    captured_latitude   NUMERIC(10, 7),
    captured_longitude  NUMERIC(10, 7),

    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(50)     NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(50),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(50),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_signatures_stop_active
    ON signatures (stop_id)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------
-- 6) driver_locations — GPS 추적 (BaseEntity 미상속 — 30일 자동 cleanup 정책)
--    captured_date    = partition key (DATE 단위 cleanup)
--    NUMERIC(10,7)    = 약 1.1cm 정확도
----------------------------------------------------------------------
CREATE TABLE driver_locations (
    id              UUID            PRIMARY KEY,
    driver_id       UUID            NOT NULL REFERENCES drivers(id),
    latitude        NUMERIC(10, 7)  NOT NULL,
    longitude       NUMERIC(10, 7)  NOT NULL,
    captured_at     TIMESTAMP       NOT NULL,
    captured_date   DATE            NOT NULL,
    source          VARCHAR(30)     NOT NULL
);

CREATE INDEX ix_driver_locations_driver_captured
    ON driver_locations (driver_id, captured_at DESC);

CREATE INDEX ix_driver_locations_captured_date
    ON driver_locations (captured_date);

----------------------------------------------------------------------
-- 7) shedlock — multi-instance race 가드 (DriverLocation 30일 cleanup scheduler)
--    dashboard-service V2 패턴 일관 (W4 후속 fix DV-W4-3).
----------------------------------------------------------------------
CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
