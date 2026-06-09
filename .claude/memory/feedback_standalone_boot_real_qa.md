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

**🚨 신규 Flyway 마이그레이션 = clean bootJar 필수 (PR #436 회고, 2026-06-09)**: 신규 `V*.sql` 추가 후 그냥 `bootJar` 하면 Gradle `processResources UP-TO-DATE` 로 **jar 에 신규 마이그레이션 미반영** → standalone QA 가 구 스키마/제약으로 부팅해 이미 fix 한 결함(예 product_spec 전체 UNIQUE 위반 롤백)을 재현. **standalone QA·배포 전 `clean :services:<svc>:bootJar`**(또는 `processResources --rerun`) 필수. 실 QA 가 이 함정을 적발(stale jar 가 V12 미반영 → 상업멀티 탭 duplicate-key 롤백, clean 재빌드로 해소).

**Why**: code-read PASS 금지·실 데이터만 원칙을 Windows Testcontainers 한계 안에서도 충족. H2 가 못 잡는 Postgres 전용 DDL(partial unique index, `IS NOT DISTINCT FROM`, COALESCE functional index)을 실 Postgres 로 검증. `@Profile("seed")` CommandLineRunner 등은 기본 무간섭.

**🚨 cross-service 실 QA DB env-var 함정 (PR #431 회고, 2026-06-08)**: 서비스마다 datasource env-var 이름이 **다르다**. auth-service = **`DB_NAME`/`DB_HOST`/`DB_PORT`**(application.yml `${DB_NAME:auth_db}`). arologis-service = **`SAMHAN_AROLOGIS_DB_NAME`** (chained `${SAMHAN_AROLOGIS_DB_NAME:${LEGACY_DB_NAME:arologis_db}}`). 격리 QA DB(예 auth_db_qa) 부팅 시 **틀린 env-var 설정 → 기본 실 DB(auth_db)에 Flyway 적용** = 운영 dev DB 전진 마이그레이션(auth V46 = accounts.role drop 같은 파괴적 forward 포함, 실행 중 stale 컨테이너 깨질 위험). **부팅 전 해당 서비스 application.yml `spring.datasource.url` placeholder 이름을 grep 확인** 필수. cross-service round-trip QA(arologis→auth internal EP) 는 양 서비스 `SAMHAN_INTERNAL_TOKEN` 동일값 + `SAMHAN_AUTH_SERVICE_URL` 로 연결. 부작용 발생 시 핸드오프에 "dev 스택 재빌드 필요" 명기.

**Electron 렌더러 실화면 QA(arologis-desktop, PR #427/#429/#431)**: `@renderer` alias 때문에 plain vite 불가 → `VITE_<API_BASE>=<alt-port> npx electron-vite build` 로 API base 베이크 후 `out/renderer` 정적 서빙(hash router 라 더미 static server 충분). Playwright `addInitScript` 로 `window.arologisAuth`(getToken/setToken/clearToken) IPC 브리지 스텁. `@playwright/test` 는 `clients/desktop` 에만 설치 → `createRequire('.../clients/desktop/package.json')`. **실화면이 정적 분석보다 강함 실증**: PR #431 매트릭스 미라벨 롤(DEVELOPER/DRIVER/PARTNER/STAFF)을 코드리뷰·크로스체크 둘 다 놓치고 실화면 캡처가 적발.
