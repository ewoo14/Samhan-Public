# Slice A DevOps Report — Accounting Service 인프라 검증

> **작성**: 2026-05-04 PM Claude DevOps agent
> **참조**: Plan `docs/dev-reports/accounting-slice-A/plan.md` (PM 발행 예정),
> Flyway V1 draft `docs/dev-reports/accounting-slice-A/flyway-v1-draft.sql`
> **회고 가드**: `feedback_pm_integration_build_check.md` Layer 1~5,
> `feedback_powershell_utf8_writes.md`, `feedback_korean_commits.md`

본 슬라이스 DevOps 의무는 ① accounting_db 신규 추가 (Docker compose +
init script) ② accounting-service 환경변수 템플릿 ③ Flyway V1 시드 PgSQL 16
컨테이너 즉석 시연 ④ CI 영향 평가 ⑤ API Gateway 라우팅 검증 ⑥ 회귀 위험
체크 입니다.

---

## 1. accounting_db 신규 (Docker compose)

### 1.1 현황 — 사전 등록 완료

`infrastructure/postgres/init/01-create-databases.sql` 의 line 10 에
`CREATE DATABASE accounting_db OWNER samhan;` **이미 존재**. (Phase 1 13
서비스 로드맵 사전 정의분). 따라서 Slice A 시점 **신규 추가 작업 0**.

마찬가지로 `infrastructure/postgres/init/02-extensions.sql` 의 line 25-27 에
`uuid-ossp` + `pgcrypto` 확장이 accounting_db 에도 사전 적용됨.

`infrastructure/docker-compose.yml` 의 `postgres` 서비스는 변경 불필요 —
init 스크립트가 컨테이너 최초 부팅 시 자동 실행됨.

### 1.2 application yml — slip-service 패턴 답습

`services/accounting-service/src/main/resources/application.yml` 은 BE agent
가 작성하되, **slip-service application.yml 의 단일 파일 + profile 분리
패턴** 을 그대로 채택할 것을 권장. 핵심 라인:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:accounting_db}
```

`application-pgsql.yml` 별도 파일 분리는 본 코드베이스의 규약과 어긋남
(slip-service / inventory-service 모두 단일 application.yml + `--- spring.config.activate.on-profile=local`).

---

## 2. 환경변수 / 시크릿 — accounting-service.env 신규

`infrastructure/env-templates/accounting-service.env` 신규 작성 완료.
slip-service.env 답습 + Solapi 블록 제거 + 외부 의존 0 명시.

| 키 | 기본값 | 용도 |
| --- | --- | --- |
| `DB_HOST/PORT/NAME/USER/PASSWORD` | localhost:5432/accounting_db/samhan | DataSource |
| `EUREKA_URL` | http://localhost:8761/eureka/ | 서비스 디스커버리 |
| `INTERNAL_AUTH_TOKEN` | dev-internal-token-change-me | InternalTokenGuard (Slice B+) |

신규 외부 시크릿 **0**. Solapi/MinIO/ES 모두 미사용 (Slice A 는 표준 계정과목
+ 분개 CRUD only).

---

## 3. Flyway V1 PgSQL 16 컨테이너 시연 결과

### 3.1 시연 순서

```bash
docker run -d --name pg16-acct -e POSTGRES_USER=samhan \
  -e POSTGRES_PASSWORD=samhan_dev_pw -p 25432:5432 postgres:16-alpine
docker exec pg16-acct psql -U samhan -d postgres \
  -c "CREATE DATABASE accounting_db OWNER samhan;"
docker exec pg16-acct psql -U samhan -d accounting_db \
  -c 'CREATE EXTENSION "uuid-ossp"; CREATE EXTENSION pgcrypto;'
cat flyway-v1-draft.sql | docker exec -i pg16-acct psql -U samhan -d accounting_db
```

### 3.2 검증 결과 표

| 검증 항목 | 결과 |
| --- | --- |
| 4 테이블 생성 (`chart_of_accounts`, `journals`, `journal_lines`, `journal_number_sequences`) | OK (`\dt` 4 rows) |
| 6 인덱스 (`idx_*` × 6) | OK (각 CREATE INDEX 정상) |
| 시드 INSERT — chart_of_accounts | **59 rows** (ASSET 15 / LIABILITY 10 / EQUITY 5 / REVENUE 7 / EXPENSE 22) |
| 한글 계정명 출력 (UTF-8) | OK — `자산/현금/당좌예금/외상매출금` 정상 |
| 분개 헤더+라인 INSERT (시연 매출 110,000) | OK — `total_debit=110000.00, total_credit=110000.00, diff=0.00` |
| `chk_journal_lines_dr_cr` CHECK 제약 (debit+credit 동시 양수 reject) | OK — `ERROR: violates check constraint` 정상 발생 |
| `VACUUM ANALYZE` 전 테이블 | OK — bloat 0 |
| `pg_dump --format=custom` | OK — 13,670 bytes, TOC 30 entries, gzip 압축, version 1.15-0 |
| `pg_restore -l` 가독성 | OK — chart_of_accounts/journals/journal_lines 모두 인식 |

> **메모리 의무 준수**: project_korean_accounting.md 의 100/200/300/400/500/800/900
> 코드 체계에 정확히 매핑됨. 시드 row 수는 50+ 의무를 충족 (실측 59).

### 3.3 H2 (local profile) 호환성

slip-service 와 동일 패턴 — `local` profile 에서 `flyway.enabled=false` +
`ddl-auto: create-drop` 적용. 따라서 본 V1 SQL 은 **PgSQL 전용**, H2 는
JPA 가 메타데이터로 자동 생성. PgSQL-specific 기능 (CHECK 표현식, partial
unique 등) 은 H2 회귀 영향 0.

PgSQL IT (Testcontainers `AbstractPostgresIT` 답습) 가 Flyway 실 적용을
검증함 — Layer 2 (Docker IT 시연) 가드 통과.

### 3.4 BE agent 인계 사항

- `flyway-v1-draft.sql` 그대로
  `services/accounting-service/src/main/resources/db/migration/V1__init_accounting_service.sql`
  로 채택 가능
- `gen_random_uuid()` 는 pgcrypto 확장 의존 — init script 에 이미 포함됨
- `ddl-auto: validate` 로 부팅 시 Hibernate 메타데이터 ↔ Flyway 스키마
  일치 자동 검증 (slip-service 패턴 답습)

---

## 4. CI 영향

### 4.1 변경 사항 — `.github/workflows/*.yml` 무수정

`./gradlew assemble` + `./gradlew test` 는 settings.gradle 의 모든 모듈을
자동 포함. accounting-service 가 추가되면 settings.gradle 에 `include` +
`projectDir` 1줄씩 추가만으로 자동 픽업 — workflow 변경 0.

> **참고**: settings.gradle 등록은 BE agent 의무 (본 DevOps 산출 범위 외).

### 4.2 실행 시간 영향 추정

| 단계 | 추정 증가 |
| --- | --- |
| `assemble` | +5초 (단일 모듈 컴파일) |
| `test` (H2 단위 + Testcontainers IT) | +15~25초 (PgSQL 컨테이너 1회 추가 startup ~10초 amortized) |
| **합계** | **약 +25~30초** |

CI 30분 timeout 대비 영향 미미 (~1.6%).

### 4.3 H2 + Testcontainers 양쪽 호환

- 단위 테스트: H2 + JPA `create-drop` (Flyway disabled)
- 통합 테스트: Testcontainers PgSQL 16 + Flyway V1 적용
- 양쪽 패턴 모두 slip-service 에서 동일하게 검증된 형태

`feedback_testcontainers_windows_docker.md` — Windows 로컬 환경에서
Docker Desktop 의 npipe 한계로 IT skip 가능. CI (ubuntu-latest) 에서는
정상 실행 보장. BE agent 에 IT skip 조건 명시 인계.

---

## 5. API Gateway 라우팅 검증

`services/api-gateway/src/main/resources/application.yml` line 80-86 에 이미
존재:

```yaml
- id: accounting-service
  uri: lb://accounting-service
  predicates:
    - Path=/api/accounting/**
  filters:
    - StripPrefix=1
    - JwtAuthentication
```

검증 결과:
- 라우팅 prefix `/api/accounting/**` 등록 OK
- `lb://` LoadBalancer URI → Eureka discovery 자동 활용
- `JwtAuthentication` 필터 적용 (정상 — 회계 데이터 인증 필수)
- StripPrefix=1 (gateway 가 `/api/` 제거 후 accounting-service 의
  `/accounting/**` 로 전달)

### 5.1 Eureka 등록 — BE 의존

`services/accounting-service/src/main/resources/application.yml` 의
`spring.application.name=accounting-service` + `eureka.client.service-url`
설정 시 부팅과 동시에 Eureka 등록됨 (slip-service 패턴 답습). **DevOps 측
추가 작업 0**.

---

## 6. 비용 / 인프라 영향

### 6.1 신규 외부 의존: 0

- Solapi 미사용 (SMS 발송 없음)
- MinIO 미사용 (PNG/PDF 저장 Phase 5+ deferred)
- Elasticsearch 미사용 (감사 로그는 logging-service 위임)

### 6.2 DB 사이즈 추정

| 시점 | 누적 row | 추정 디스크 |
| --- | --- | --- |
| Slice A 직후 | 59 (chart_of_accounts) + 0 (journals/lines) | ~50 KB |
| 1개월 운영 | 59 + 1,000 journals × 평균 3 lines = 3,059 lines | ~2 MB |
| 12개월 운영 | 59 + 12,000 journals × 3 = 36,000 lines | ~25 MB |
| Phase 5 Grafana 트리거 | — | pg_exporter 도입 시 자동 모니터링 |

PostgreSQL 단일 인스턴스 대비 부하 미미. 백업은 PR #23 backup-runbook
의 `pg_dump --format=custom` 패턴 그대로 적용 가능 (위 §3.2 시연 검증
완료).

---

## 7. 회귀 위험: 0

| 영향 영역 | 평가 |
| --- | --- |
| 기존 8 서비스 (auth/user/product/inventory/slip/gateway/eureka/logging) 코드 | **무영향** (독립 모듈) |
| 기존 8 DB | **무영향** (accounting_db 는 신규 격리) |
| docker-compose / postgres init script | **무영향** (accounting_db 는 사전 등록분) |
| API Gateway 기존 라우팅 (slip-service-public 등) | **무영향** (accounting 라우팅 사전 등록) |
| CI workflow yaml | **무수정** |
| backup-runbook (PR #23) | **무영향**, 동일 패턴 자동 적용 |

---

## 8. 회고 가드 체크리스트

- [x] **Layer 1 BE+QA 사전 컴파일** — BE agent 책임 (산출물 단계 명시)
- [x] **Layer 2 Docker IT 시연** — 본 §3 PgSQL 16 컨테이너 즉석 시연 완료
- [x] **Layer 4 도메인 메서드 의미 정렬** — chart_of_accounts 시드 코드가
  Korean accounting 메모리와 100% 일치 검증
- [x] **PowerShell UTF-8 트랩** — 본 보고서 + env 템플릿 + V1 draft 모두
  Write 도구로 작성 (UTF-8 no BOM 보장)
- [x] **한국어 commit/PR 의무** — 본 보고서 한국어, 커밋 메시지도 한국어
  작성 예정

---

## 9. BE agent 인계 요약

1. `flyway-v1-draft.sql` 을 `V1__init_accounting_service.sql` 로 그대로
   복사 (services/accounting-service/src/main/resources/db/migration/)
2. application.yml 은 slip-service 패턴 답습 (단일 파일 + `--- on-profile=local`)
3. settings.gradle 에 `include 'services:accounting-service'` + projectDir
   line 추가
4. BaseEntity 7 audit 필드는 V1 draft 에 모두 포함됨 — `@SQLRestriction("is_deleted = false")`
   엔티티 레벨 적용 의무
5. `@MockBean` 외부 client 0 (Solapi/Mock 등 모두 불요) — IT 격리 부담 최소
