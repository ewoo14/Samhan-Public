-- SamhanLogis - per-service PostgreSQL databases (Phase 1 ~ 10).
-- Executed once on first container start by docker-entrypoint-initdb.d.
-- Owner: samhan (POSTGRES_USER, set in docker-compose.yml).
--
-- 풀 수준 로컬 테스트 환경 보강 (feature/local-test-setup) — Phase 6/9/10 신규
-- service 의 DB (logging_db / partner_order_db / dc_config_db / partner_auth_db) 추가.
-- 검증: `docker compose down -v && docker compose up -d postgres`
--       → `docker exec samhan-postgres psql -U samhan -l` 으로 16개 DB 확인.

-- Phase 1 ~ 4
CREATE DATABASE auth_db          OWNER samhan;
CREATE DATABASE logging_db       OWNER samhan;
CREATE DATABASE user_db          OWNER samhan;
CREATE DATABASE product_db       OWNER samhan;
CREATE DATABASE inventory_db     OWNER samhan;
CREATE DATABASE slip_db          OWNER samhan;
CREATE DATABASE accounting_db    OWNER samhan;

-- Phase 6 (legacy 마이그레이션 — partner-auth / dc-config / partner-order)
CREATE DATABASE partner_auth_db  OWNER samhan;
CREATE DATABASE dc_config_db     OWNER samhan;
CREATE DATABASE partner_order_db OWNER samhan;

-- Phase 9 (잔여 도메인 4 신규 service)
CREATE DATABASE partner_db       OWNER samhan;
CREATE DATABASE groupware_db     OWNER samhan;
CREATE DATABASE notification_db  OWNER samhan;
CREATE DATABASE dashboard_db     OWNER samhan;

-- Phase 10 (arologis-service)
CREATE DATABASE arologis_db      OWNER samhan;

-- Phase 11 (renumber, AWS migration)
CREATE DATABASE migration_db     OWNER samhan;
