-- V11__concurrently_signature_indexes.sql
-- Slip Service — Phase 10 W10-4 잔존 fix (PR #99) — DV-2 운영 가드 흡수 (D-P10-15 사용자 강화 가드 채택)
--
-- 목적:
--   V10 의 ix_slips_signature_source_app / ix_slips_driver_signature_source_app 2종 partial index 를
--   CONCURRENTLY 변형으로 재생성한다. ACCESS EXCLUSIVE lock 회피 + 대용량 slips 테이블 (~1M+ rows)
--   영향 최소화 (Phase 11 cutover 진입 시점 운영 RDS Aurora 부하 안전성).
--
-- 운영 진입 시 Flyway 동작:
--   * Flyway 는 기본적으로 transaction 내부에서 마이그레이션 실행 — Postgres 의 CREATE INDEX
--     CONCURRENTLY 는 transaction block 내부 실행 불가.
--   * 본 파일과 동일 디렉토리의 V11__concurrently_signature_indexes.sql.conf 가 executeInTransaction=false
--     설정으로 본 SQL 의 transaction 진입을 차단한다 (Flyway 9.x+ script-config 표준 패턴).
--
-- 회귀 영향:
--   * V10 의 partial WHERE 절 (is_deleted = FALSE AND signature_source = 'APP' AND signed_at IS NOT NULL) 보존.
--   * IF EXISTS / IF NOT EXISTS 가드 — 신규 환경 (V10 적용 직후) 과 운영 환경 (V10 + 대용량 데이터) 모두 호환.
--   * dev/staging 환경에서는 partial index 가 대용량 lock 영향이 거의 없으므로 본 마이그레이션이
--     의미 있는 차이를 만드는 시점은 production cutover (signatures 테이블 ~1M rows 누적 후).
--
-- dev-report § 11-3 (Flyway V10/V11 lock 영향 시뮬레이션) + § 11-1 (signature_source 운영 데이터 분류 검증) 참조.

----------------------------------------------------------------------
-- 1) ix_slips_signature_source_app — 인수자 APP 서명 partial index 재생성
----------------------------------------------------------------------
DROP INDEX IF EXISTS ix_slips_signature_source_app;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_slips_signature_source_app
    ON slips (signed_at DESC)
    WHERE is_deleted = FALSE AND signature_source = 'APP' AND signed_at IS NOT NULL;

----------------------------------------------------------------------
-- 2) ix_slips_driver_signature_source_app — 기사 APP 서명 partial index 재생성
----------------------------------------------------------------------
DROP INDEX IF EXISTS ix_slips_driver_signature_source_app;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_slips_driver_signature_source_app
    ON slips (driver_signed_at DESC)
    WHERE is_deleted = FALSE AND driver_signature_source = 'APP' AND driver_signed_at IS NOT NULL;
