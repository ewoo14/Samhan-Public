# Phase 8 1차 — AWS 호환성 가드 plan + 12-factor 검증 + 환경변수 표준 + ROADMAP/DECISIONS 갱신

본 보고서는 Phase 8 1차 슬라이스의 산출물 + 12-factor 준수 검증 + AWS 서비스 매핑 표 + 후속 작업 계획을 정리한다.

---

## 1. 슬라이스 범위

- **목표** = AWS 마이그레이션 가능성을 열어두는 호환성 가드 plan + 검증 (코드 변경 X, docs only)
- **현재 인프라** = 카페24 + Cloudflare Pages + Render 그대로 유지
- **마이그레이션 timeline** = 현재 = 테스트 단계, 모든 개발 완료 후 (Phase 10 또는 별도 슬라이스) AWS cutover

---

## 2. 산출물 매트릭스

| # | 산출물 | 위치 | 상태 |
|---|---|---|---|
| 1 | AWS 호환성 가드 plan | `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md` | NEW |
| 2 | 환경변수 표준화 plan | `docs/migration/phase8/M-ENV-STANDARDIZATION.md` | NEW |
| 3 | ROADMAP.md 갱신 (Phase 7 완료, Phase 8/9/10 재정의) | `ROADMAP.md` | UPDATE |
| 4 | DECISIONS.md 갱신 (D-P8-03 ~ D-P8-06) | `migration/decisions/DECISIONS.md` | UPDATE |
| 5 | dev-report (본 보고서) | `docs/dev-reports/phase8-step-1-aws-readiness.md` | NEW |

> 코드 변경 = 0 file. legacy 비즈니스 로직 변형 X.

---

## 3. 12-factor 준수 검증 결과 (12 service)

| Factor | 항목 | 현재 상태 | 비고 |
|---|---|---|---|
| I  | Codebase | OK | `ewoo14/SamhanLogis` 단일 repo |
| II | Dependencies | OK | Gradle / npm lock |
| III| Config | OK | `${ENV:default}` 패턴 12 service 모두 적용 |
| IV | Backing services | OK | DB / Redis / RabbitMQ / S3 환경변수 attach |
| V  | Build / release / run | OK | Docker + GitHub Actions |
| VI | Stateless processes | OK | JWT session, 파일은 별도 storage |
| VII| Port binding | OK | application.yml `server.port` 명시 |
| VIII| Concurrency | OK | Spring Boot thread pool, horizontal scale OK |
| IX | Disposability | OK (부분) | Spring Boot 기본 graceful, Phase 10 `server.shutdown=graceful` 명시 권장 |
| X  | Dev/prod parity | OK | Testcontainers (PostgreSQL 16) parity |
| XI | Logs (stdout) | OK | logging-service consumer |
| XII| Admin processes | OK | Flyway + seed runner |

→ **12-factor 통과** (12/12 OK, IX 만 Phase 10 개선 항목 1건).

---

## 4. AWS 서비스 매핑 결과

| 현재 | AWS 대상 | 호환성 |
|---|---|---|
| Cloudflare Pages | Amplify / S3 + CloudFront | OK |
| Render | App Runner / ECS Fargate | OK |
| 카페24 VPS | EC2 + ALB + ASG | OK |
| docker-compose Postgres 16 | RDS PostgreSQL 16 | OK |
| docker-compose Redis 7 | ElastiCache Redis 7 | OK |
| docker-compose RabbitMQ 3.13 | AWS MQ (RabbitMQ 3.13) | OK |
| docker-compose Elasticsearch 8.15 | OpenSearch Service 2.x | OK (마이너 차이) |
| docker-compose MinIO | S3 (endpoint override) | OK |
| Spring Cloud Eureka | Cloud Map (wrapper) 또는 자체 운영 | wrapper 또는 자체 |
| Resilience4j | 그대로 | OK |
| Spring Cloud Gateway | 그대로 (EC2/ECS) | OK |
| Cloudflare DNS | Route 53 | OK |
| Prometheus / Grafana | Managed 또는 EC2 자체 | OK |
| `*.samhan-air.com` | Route 53 + ACM | OK |

→ **wrapper 필요 = 1건 (Eureka)**, 나머지는 endpoint 변경만으로 호환.

---

## 5. PostgreSQL standard SQL 검증 결과

22 file Flyway migration `services/*/src/main/resources/db/migration/V*.sql` 분석:

### 사용중 (RDS 호환 OK)
- `JSONB` + `jsonb_path_ops` GIN index — dc-config / product / partner-auth / slip
- `NUMERIC(N, M)` — product / accounting
- `VARCHAR(N)` — 모든 service (CHAR(N) 금지 가드 적용중)
- `TIMESTAMP WITH TIME ZONE` — BaseEntity audit
- `UUID` (Hibernate generated)
- Partial unique index (PostgreSQL 11+)

### 미사용 (RDS 비호환 또는 외부 extension 의존)
- TimescaleDB / Citus / `LISTEN`/`NOTIFY` / Oracle `CONNECT BY` / Oracle `ROWNUM` / MS SQL `TOP N` / MySQL backtick — 모두 미사용 OK

→ **PostgreSQL 표준 SQL 가드 통과** (RDS 16 직접 마이그레이션 가능).

---

## 6. 환경변수 추상화 검증 결과

### OK
- 12 service 모두 `${ENV:default}` 패턴
- backing service (DB / Eureka / RabbitMQ / Elasticsearch) 모두 환경변수
- secret credential 모두 env (default = `dev-` prefix 또는 `CHANGE_ME_LOCAL_ONLY`)
- hard-coded URL = `localhost` default 만 (env override 가능)

### 검출 불일치 (3건, 본 슬라이스 범위 외)
- `INTERNAL_TOKEN` (1) vs `INTERNAL_AUTH_TOKEN` (6) — Phase 9 통일
- `DC_CONFIG_HOST` (1) vs `DC_CONFIG_URL` (1) — Phase 9 통일
- `.env.example` 12 service 중 부분만 보유 — Phase 9 의무 추가

→ **환경변수 추상화 통과** (검출 불일치 3건은 Phase 9 슬라이스 위임).

---

## 7. ROADMAP / DECISIONS 변경 요약

### ROADMAP.md
- Phase 7 = "완료" (PR #87 머지)
- Phase 8 = "AWS 호환성 가드 (테스트 단계 유지)" — 직접 cutover 보류
- Phase 9 = "잔여 도메인 (partner / groupware / notification / dashboard)"
- Phase 10 = "AWS 마이그레이션 + Migration Service + 운영 안정화"
- 머지 PR 매트릭스 — #87 + 본 PR 추가

### DECISIONS.md (4건 신규)
- D-P8-03 — 호스팅 = AWS (EC2 + RDS) 향후 예정 (Phase 10 cutover)
- D-P8-04 — 현재 = 테스트 단계, 카페24 + Cloudflare + Render 그대로 유지
- D-P8-05 — AWS 마이그레이션 가능성을 열어두는 호환성 가드 검증 의무
- D-P8-06 — Phase 8 1차 = AWS 호환성 가드 plan + 검증 (본 PR)

---

## 8. Phase 8 후속 작업

| 차수 | 산출물 | 의존 |
|---|---|---|
| 2차 | Eureka cluster prod 설정 (다중 노드) | 본 PR 머지 |
| 3차 | Resilience4j prod 임계치 정착 + AWS 마이그레이션 dry-run plan | 2차 머지 |
| 4차 | API Gateway production routing + rate limit (현재 인프라) | 3차 머지 |
| 5차 | 모니터링 alert (Prometheus → Grafana → Slack/SMS) | 4차 머지 |

> Phase 8 4~5차 = 현재 인프라 (카페24 + Cloudflare + Render) 기준 운영 가드. AWS cutover 는 Phase 10.

---

## 9. 가드 적용 결과

| 가드 | 준수 여부 |
|---|---|
| 한국어 commit / 한국어 docs | OK |
| 단편 PR 회피 — 1 통합 PR | OK |
| legacy 비즈니스 로직 변형 X | OK (코드 변경 0 file) |
| placeholder 값만 (시크릿 X) | OK |
| AWS timeline = "현재 X, 모든 개발 완료 후" 명시 | OK |
| origin/main 동기화 | OK (8dc9808 → 541db45 fetch + branch base) |

---

## 10. 참조

- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- 환경변수 표준: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- 누적 결정: `migration/decisions/DECISIONS.md`
- ROADMAP: `ROADMAP.md`
- Phase 8 readiness plan: `docs/migration/phase8/M-PHASE-8-readiness.md`
