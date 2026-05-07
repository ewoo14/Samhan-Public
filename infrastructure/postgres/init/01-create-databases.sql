-- SamhanLogis - per-service PostgreSQL databases (Phase 1 + roadmap)
-- Executed once on first container start by docker-entrypoint-initdb.d.
-- Owner: samhan (POSTGRES_USER, set in docker-compose.yml).

CREATE DATABASE auth_db        OWNER samhan;
CREATE DATABASE user_db        OWNER samhan;
CREATE DATABASE product_db     OWNER samhan;
CREATE DATABASE inventory_db   OWNER samhan;
CREATE DATABASE slip_db        OWNER samhan;
CREATE DATABASE accounting_db  OWNER samhan;
CREATE DATABASE partner_db     OWNER samhan;
CREATE DATABASE groupware_db   OWNER samhan;
CREATE DATABASE notification_db OWNER samhan;
CREATE DATABASE dashboard_db   OWNER samhan;
CREATE DATABASE migration_db   OWNER samhan;
CREATE DATABASE arologis_db    OWNER samhan;
