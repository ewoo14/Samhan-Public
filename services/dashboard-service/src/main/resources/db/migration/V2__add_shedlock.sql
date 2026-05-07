-- V2__add_shedlock.sql
-- Phase 9 W4 후속 fix (DevOps DV-W4-3) — ShedLock 분산 lock 표준 schema.
--
-- multi-instance scaling 시점에 REFRESH MATERIALIZED VIEW CONCURRENTLY 가
-- 동일 view 에 대해 여러 instance 에서 동시 실행되는 race 를 lock 으로 회피.
-- single-instance 환경에서도 안전 (lock 즉시 획득 + 본인 해제).
--
-- 표준 schema (ShedLock 5.x JDBC provider 기본값):
--   * name        — lock 이름 (PK)
--   * lock_until  — lock 유효 기한 (만료 시 다른 instance 가 획득 가능)
--   * locked_at   — lock 획득 시각
--   * locked_by   — lock 획득 instance 식별자 (hostname 등)

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
