---
name: standalone-boot-real-qa
description: Testcontainers IT 가 Windows 로컬 skip 될 때 서비스 jar standalone 부팅으로 실 Postgres+외부소스 실 QA
metadata:
  type: feedback
---

Windows 회사 PC 에서 Testcontainers 기반 IT 는 `DockerAvailableCondition` 이 Docker(npipe) 미감지로 **항상 skip**(Docker Desktop 떠 있어도). DOCKER_HOST=tcp://2375·npipe 우회 모두 무효 — CI Linux 만 실제 실행([[testcontainers-windows-docker]]).

**그래도 머지 전 Docker 실서버 실 QA([[qa-docker-real-test]]+[[no-fake-data-ever]])는 가능**: 외부 인프라 의존 0 인 서비스(예 product-service = Postgres 만, Redis/Kafka 무)는 **jar standalone 부팅**으로 실측.

**How to apply** (PR #425 검증 패턴):
1. `./gradlew :services:<svc>:bootJar`
2. `docker run -d --name qa-pg -e POSTGRES_DB=<db> -e POSTGRES_USER=samhan -e POSTGRES_PASSWORD=samhan_dev_pw -p 5433:5432 postgres:16-alpine`
3. `DB_HOST=localhost DB_PORT=5433 DB_NAME=<db> ... <외부자격 env, 예 GOOGLE_SERVICE_ACCOUNT_KEY> java -jar build/libs/<svc>.jar --eureka.client.enabled=false --app.scheduling.enabled=true --app.scheduling.<cron>="0 0 0 1 1 ?"`(cron 먼미래로 잠그고 ApplicationReadyEvent boot sync 만 1회 실행). `local` 프로파일 금지(H2 라 Postgres 전용 SQL 미검증) — default 프로파일 + override.
4. boot 로그에서 Flyway 버전·sync 완료 확인 → `docker exec qa-pg psql -U samhan -d <db> -c "SELECT ..."` 로 실 row 수/정직성 검증.
5. **재부팅 2차 sync 로 idempotency 실증**: `inserted=0, softDeleted=0` 이면 정상(in-memory rowHash 캐시 cold-start 로 updated=N 은 동일값 재기록 무해). NUMERIC scale 정합(5.5 vs 5.50) 같은 동시성 fix 도 이 2차 sync 로 실증 가능.
6. 종료: `Stop-Process` (java.exe CommandLine like `*<svc>.jar*`) + `docker rm -f qa-pg`.

**Why**: code-read PASS 금지·실 데이터만 원칙을 Windows Testcontainers 한계 안에서도 충족. H2 가 못 잡는 Postgres 전용 DDL(partial unique index, `IS NOT DISTINCT FROM`, COALESCE functional index)을 실 Postgres 로 검증. `@Profile("seed")` CommandLineRunner 등은 기본 무간섭.
