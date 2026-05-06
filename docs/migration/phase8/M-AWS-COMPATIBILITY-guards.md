# M-AWS-COMPATIBILITY-guards — AWS 마이그레이션 가능성을 열어두는 호환성 가드

본 문서는 SamhanLogis 가 향후 AWS (EC2 + RDS) 로 마이그레이션 가능하도록, 현재 코드베이스가 cloud-agnostic 패턴을 유지하고 있는지 검증한다. **현재 인프라 = 카페24 + Cloudflare Pages + Render 그대로 유지**, 본 가드는 단순 호환성 검증 only — 직접 cutover 가 아님.

마이그레이션 timeline:
- **현재 = 테스트 단계** (모든 개발 진행)
- **모든 개발 완료 후** = AWS 마이그레이션 cutover (Phase 10 또는 별도 슬라이스)
- 본 가드 = 현재 코드가 AWS 마이그레이션 가능성을 열어두는 패턴 검증 only

---

## 1. 12-factor app 검증 매트릭스

본 매트릭스는 현재 12 backend service (Phase 9 추가 예정 4개 = 14) 의 12-factor 준수 상태를 정리한다.

### 검증 대상 service (12개)

| # | service | 포트 | 도입 Phase |
|---|---|---|---|
| 1 | eureka-server | 8761 | 1 |
| 2 | api-gateway | 8080 | 1 |
| 3 | auth-service | 8081 | 1 |
| 4 | logging-service | 8082 | 1 |
| 5 | user-service | 8083 | 2 |
| 6 | product-service | 8084 | 2 / 6 |
| 7 | inventory-service | 8085 | 2 |
| 8 | slip-service | 8086 | 2 / 3 / 6 |
| 9 | accounting-service | 8087 | 4 |
| 10 | partner-auth-service | 8091 | 6 |
| 11 | dc-config-service | 8089 | 6 |
| 12 | partner-order-service | (8090 reserved) | 6 |

> Phase 9 추가 예정 — partner-service (8095, 8088 충돌 회피) / groupware-service (8092) / notification-service (8093) / dashboard-service (8094). 본 가드는 12 + 4 = 14 final 기준 적용. (Phase 10 migration-service = 8096)

### 12-factor 준수 검증

| Factor | 항목 | 현재 상태 | 비고 |
|---|---|---|---|
| I  | Codebase — 단일 git repo | OK | `ewoo14/SamhanLogis` 단일 repo, multi-project Gradle |
| II | Dependencies — 명시적 lock | OK | Gradle (`build.gradle` + lock) / npm (`package-lock.json`) |
| III| Config — 환경변수만 사용 | OK | 모든 `application.yml` `${ENV:default}` 패턴 (D-P8 §1-2) |
| IV | Backing services — attach | OK | DB / Redis / RabbitMQ / S3 모두 환경변수로 attach (`DB_HOST`, `REDIS_HOST`, `RABBIT_HOST`, `S3_ENDPOINT`) |
| V  | Build / release / run | OK | Docker + GitHub Actions (`.github/workflows/ci.yml` + `deploy-*.yml`) |
| VI | Processes — stateless | OK | session = JWT (HS256, payload-only), file = MinIO/S3 (별도 storage), in-memory state X |
| VII| Port binding — 명시 | OK | 각 service `application.yml` `server.port` 명시 |
| VIII| Concurrency — process model | OK | Spring Boot 3 single JVM + thread pool (Tomcat default 200), 추가 인스턴스 = horizontal scale |
| IX | Disposability — graceful | OK (부분) | Spring Boot 기본 graceful shutdown 활성, `management.endpoints.web.exposure.include=health` 가용. **개선 — `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=30s` Phase 10 추가 권장** |
| X  | Dev/prod parity | OK | Testcontainers (PostgreSQL 16) + docker-compose (PostgreSQL 16-alpine) parity |
| XI | Logs — stdout/stderr | OK | 모든 service `org.springframework.boot.logging` stdout, logging-service consumer 가 RabbitMQ → Elasticsearch sink |
| XII| Admin processes | OK | Flyway migration (`V1~V8`), seed runner (product-service `SEED_DRY_RUN_MODE`) |

### Phase 10 개선 항목 (현재 X, AWS 마이그레이션 시 권장)

- IX. Disposability — `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s` 명시 (현재 default = `IMMEDIATE`). ECS Fargate / EKS rolling deploy 시 connection drain 안전성 확보.
- XII. Admin — Flyway `outOfOrder: false` + `validateOnMigrate: true` production profile 활성 (현재 default).

---

## 2. 환경변수 추상화 검증

### 2-1. backing service 환경변수 (현재 사용중)

| 카테고리 | 환경변수 | default | AWS 교체 대상 |
|---|---|---|---|
| DB | `DB_HOST` | `localhost` | RDS endpoint (`*.rds.amazonaws.com`) |
| DB | `DB_PORT` | `5432` | RDS port (5432 그대로) |
| DB | `DB_USER` | `samhan` 또는 `CHANGE_ME_LOCAL_ONLY` | RDS master user (Secrets Manager) |
| DB | `DB_PASSWORD` | `samhan_dev_pw` 또는 `CHANGE_ME_LOCAL_ONLY` | RDS master password (Secrets Manager) |
| DB | `DB_NAME` | `<service>_db` | RDS database name |
| Eureka | `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka cluster ALB URL 또는 AWS Cloud Map |
| Eureka | `EUREKA_PEER_URL` | `http://eureka-peer:8761/eureka/` | peer instance URL |
| Eureka | `EUREKA_INSTANCE_HOSTNAME` | `eureka-peer1` | EC2 instance hostname |
| RabbitMQ | `RABBIT_HOST` | `localhost` | AWS MQ (RabbitMQ engine) endpoint |
| RabbitMQ | `RABBIT_PORT` | `5672` | AWS MQ port |
| RabbitMQ | `RABBIT_USER` / `RABBIT_PASSWORD` | `samhan` / `samhan_dev_pw` | AWS MQ credential (Secrets Manager) |
| Elasticsearch | `ES_URI` | `http://localhost:9200` | AWS OpenSearch Service endpoint |
| Auth | `JWT_SECRET` | `dev-secret-change-me-...` | Secrets Manager rotated value |
| Auth | `INTERNAL_AUTH_TOKEN` / `INTERNAL_TOKEN` | `dev-internal-token-change-me` | Secrets Manager rotated value |
| Service URL | `PRODUCT_HOST` / `INVENTORY_HOST` / `SLIP_HOST` / `DC_CONFIG_HOST` / `PARTNER_AUTH_HOST` / `DC_CONFIG_URL` | `<service>` (Docker DNS) | EC2 private IP / Cloud Map service name |
| External | `ALIGO_API_KEY` / `ALIGO_USER_ID` / `ALIGO_SENDER_PHONE` / `ALIGO_BASE_URL` | placeholder | Secrets Manager (rotated 분기별) |
| External | `GOOGLE_SHEETS_SHEET_ID` / `GOOGLE_SERVICE_ACCOUNT_KEY` / `GOOGLE_SHEETS_CACHE_TTL_MIN` | sheet ID + path | Parameter Store (config) + Secrets Manager (key file) |
| Public | `PUBLIC_BASE_URL` | `https://sign.samhan-air.com` | DNS 도메인 그대로 (Route 53 record 이전만) |
| Warehouse UUID | `WAREHOUSE_UUID_HQ` / `WAREHOUSE_UUID_HUBAL` / `WAREHOUSE_UUID_ANSEONG` / `WAREHOUSE_UUID_CHANGWON` | placeholder UUID | Parameter Store (config) |
| Sync schedule | `PRODUCT_SYNC_SCHEDULING_ENABLED` / `PRODUCT_SYNC_CRON` | `true` / hourly | Parameter Store (config) |

### 2-2. S3 / MinIO 추상화 (현재 X, Phase 10 추가)

현재 SamhanLogis 는 file storage 직접 사용 service 없음 (signature image = base64 inline DB column). 향후 file storage 도입 시:

- `S3_ENDPOINT` — MinIO `http://minio:9000` / AWS `https://s3.<region>.amazonaws.com`
- `S3_REGION` — MinIO `us-east-1` (default) / AWS `ap-northeast-2`
- `S3_ACCESS_KEY` / `S3_SECRET_KEY` — Secrets Manager
- `S3_BUCKET` — bucket name
- AWS SDK v2 (S3Client) 가 endpoint override 지원 → MinIO 와 동일 SDK 사용 가능 (vendor lock-in 회피)

### 2-3. secrets vs config 분리

| 카테고리 | 현재 위치 | AWS 권장 위치 |
|---|---|---|
| secrets (rotation 필요) | `.env` (gitignored) | AWS Secrets Manager (rotation lambda) |
| config (rotation X) | `application.yml` env default | AWS Systems Manager Parameter Store |

### 2-4. application.yml 검증 grep 결과

(2026-05-06 기준 — `services/*/src/main/resources/application*.yml` grep `\$\{[A-Z_]+:`)

- 환경변수 placeholder 사용: 12 service 모두 OK
- hard-coded URL 사용: 없음 (`localhost` 는 default 만)
- hard-coded credential 사용: 없음 (default 모두 `dev-` prefix 또는 `CHANGE_ME_LOCAL_ONLY`)

---

## 3. PostgreSQL standard SQL 가드

### 3-1. 사용중인 PostgreSQL 기능 (RDS 호환)

Flyway migration `services/*/src/main/resources/db/migration/V*.sql` (전체 22 file) 분석:

| 기능 | 사용 여부 | RDS PostgreSQL 호환 | 출현 file |
|---|---|---|---|
| `JSONB` 컬럼 | OK | OK (PostgreSQL 9.4+) | dc-config V1, product V1, partner-auth V1, slip V8 |
| `JSONB` GIN index (`jsonb_path_ops`) | OK | OK | dc-config V1, product V1 |
| `NUMERIC(N, M)` | OK | OK | product V1 (price), accounting (amount) |
| `VARCHAR(N)` (CHAR(N) 금지 가드 적용) | OK | OK | 모든 service |
| `TIMESTAMP WITH TIME ZONE` | OK | OK | BaseEntity audit columns |
| `BIGSERIAL` (auto-increment) | 일부 | OK | (Hibernate IDENTITY 또는 UUID) |
| `UUID` (Hibernate generated) | OK | OK | 모든 entity PK (Java 측 `UUID.randomUUID()`) |
| Partial unique index (`WHERE` 조건) | OK | OK (PostgreSQL 11+) | (있음 — soft delete 경합 방지) |
| `gen_random_uuid()` (DB 측 UUID) | X | OK 가능 (pgcrypto) | 사용 안 함 — Hibernate 측 generation |
| `pgcrypto` extension | X | OK 가능 (RDS 활성화 가능) | 사용 안 함 |
| `uuid-ossp` extension | X | OK 가능 (RDS 활성화 가능) | 사용 안 함 |

### 3-2. RDS 미지원 / 비표준 기능 (부재 검증)

| 기능 | 사용 여부 | 비고 |
|---|---|---|
| TimescaleDB extension | 없음 | RDS 미지원 — 사용 X 확인 |
| Citus extension (sharding) | 없음 | RDS 미지원 — 사용 X 확인 |
| `LISTEN` / `NOTIFY` | 없음 | RDS 지원이지만 horizontal scale 비호환 — 사용 X (RabbitMQ 사용) |
| Custom procedural language (PL/Python, PL/v8) | 없음 | RDS 일부 지원 — 사용 X 확인 |
| Oracle `CONNECT BY ... PRIOR` | 없음 | PostgreSQL 호환 X — 사용 X (재귀 CTE 사용 가능) |
| Oracle `ROWNUM` | 없음 | PostgreSQL 호환 X — 사용 X (`LIMIT` 사용) |
| MS SQL `TOP N` | 없음 | PostgreSQL 호환 X — 사용 X |
| MySQL backtick identifier | 없음 | PostgreSQL 호환 X — 사용 X |

### 3-3. PostgreSQL 16 → RDS PostgreSQL 호환

- 현재 docker-compose: `postgres:16-alpine`
- RDS PostgreSQL 지원 버전: 11.x ~ 16.x (2026-05 기준)
- 호환 OK — 그대로 마이그레이션 가능
- AWS DMS (Database Migration Service) 또는 `pg_dump` / `pg_restore` 으로 일괄 이관 가능

---

## 4. AWS 서비스 매핑 표

| 현재 인프라 | AWS 마이그레이션 대상 | 호환성 | 비고 |
|---|---|---|---|
| Cloudflare Pages (order-app v4 정적) | AWS Amplify Hosting / S3 + CloudFront | OK | 정적 HTML/JS — 그대로 deploy |
| Render Express SSR (estimate-app v2) | AWS App Runner / ECS Fargate / EC2 | OK | Node.js 20 standard, Express 4 — 의존 X |
| 카페24 VPS PM2 (대상 미정) | AWS EC2 + ALB + Auto Scaling Group | OK | PM2 → systemd 또는 ECS Fargate 전환 |
| docker-compose PostgreSQL 16-alpine | RDS PostgreSQL 16 | OK | Flyway migration V1~V8 그대로 적용 |
| docker-compose Redis 7-alpine | AWS ElastiCache for Redis 7 | OK | Spring Data Redis client 호환 |
| docker-compose RabbitMQ 3.13 | AWS MQ (RabbitMQ engine 3.13) | OK | spring-boot-starter-amqp 호환 |
| docker-compose Elasticsearch 8.15 | AWS OpenSearch Service 2.x | OK (마이너 차이) | OpenSearch = Elasticsearch 7.10 fork — index API 일부 차이 검증 필요 |
| docker-compose MinIO | AWS S3 | OK | S3 SDK API 일관 (endpoint 만 변경) |
| Eureka (Spring Cloud) | Eureka cluster 자체 EC2 운영 (권장) 또는 AWS Cloud Map | **wrapper 불필요 (자체 EC2 운영)** | Cloud Map 도입 시 wrapper 필요하나 자체 운영 권장 — 단순 |
| Resilience4j (in-process) | 그대로 | OK | library, infra 의존 X |
| Spring Cloud Gateway (api-gateway) | 그대로 (EC2/ECS) 또는 AWS API Gateway | OK | 그대로 권장 (JWT filter custom 보존) |
| Cloudflare DNS | AWS Route 53 | OK | DNS only — record 이전 |
| Cloudflare WAF (Phase 8 예정) | AWS WAF | OK | OWASP top 10 rule 동등 |
| GitHub Actions CI | 그대로 | OK | self-hosted runner 또는 AWS CodeBuild 옵션 |
| Prometheus + Grafana | AWS Managed Prometheus + Managed Grafana | OK | 또는 EC2 자체 운영 그대로 |
| `*.samhan-air.com` | Route 53 hosted zone | OK | NS record 이전 + ACM (TLS cert) |

### 4-1. AWS 매핑 분류 (16건 호환 + 1건 자체 운영)

**16건 호환 (endpoint / 설정 변경만)**:
Cloudflare Pages, Render, 카페24 VPS, Postgres 16, Redis 7, RabbitMQ 3.13, Elasticsearch 8.15, MinIO, Resilience4j, Spring Cloud Gateway, Cloudflare DNS, Cloudflare WAF, GitHub Actions CI, Prometheus + Grafana, `*.samhan-air.com`, ACM (TLS).

**1건 자체 EC2 운영 (wrapper 부재)**:

**Eureka cluster — 자체 EC2 운영 권장**
- 현재 = Spring Cloud Eureka (`eureka.client.service-url.defaultZone`)
- 권장 = Eureka cluster 자체를 EC2 에 운영 (다중 노드, AZ-aware) → **wrapper 불필요**
- 이유 = AWS Cloud Map wrapper 작성 부담 회피 + Spring Cloud Eureka 그대로 보존 + 단순
- AWS Cloud Map 도입 시 = `spring-cloud-aws-discovery` starter + 추상화 wrapper 필요 (선택지로만 보존, 본 plan 비채택)

→ wrapper 작성 의무 = **0건** (Eureka 자체 운영 시).

---

## 5. 마이그레이션 timeline

| 단계 | 시점 | 작업 |
|---|---|---|
| 현재 (테스트 단계) | 2026-05 ~ Phase 9 완료 | 모든 개발 진행, 카페24 + Cloudflare + Render 그대로 유지 |
| Phase 10 진입 | 모든 개발 완료 후 | AWS 마이그레이션 cutover plan 수립 (별도 슬라이스) |
| Phase 10 cutover | TBD | RDS / EC2 / S3 / Route 53 일괄 cutover |

**현재 단계에서 X 항목**:
- 직접 AWS 리소스 생성 X
- AWS account 발급 X
- production traffic AWS 전환 X
- `infrastructure/aws/` 디렉토리 생성 X (Phase 10 시점)
- `terraform/` 또는 CDK 코드 생성 X (Phase 10 시점)

**현재 단계에서 OK 항목**:
- 호환성 가드 검증 (본 문서)
- 12-factor 준수 유지 (모든 후속 슬라이스)
- 환경변수 추상화 유지 (모든 후속 service)
- standard SQL 사용 유지 (모든 후속 Flyway migration)

---

## 6. 후속 작업

### Phase 8 후속 (본 슬라이스 외)

1. Phase 8 2차 — Eureka cluster prod 설정 (다중 노드, AZ-aware 옵션은 Phase 10)
2. Phase 8 3차 — Resilience4j prod 임계치 정착
3. Phase 8 4차 — API Gateway production routing + rate limit (현재 인프라 기준)
4. Phase 8 5차 — 모니터링 alert (Prometheus → Grafana → Slack/SMS)

### Phase 10 (AWS 마이그레이션 본격)

1. AWS account 발급 + IAM baseline
2. RDS PostgreSQL 16 instance 생성 + V1~V8 적용
3. ElastiCache Redis 7 cluster
4. AWS MQ (RabbitMQ engine) cluster
5. ECS Fargate cluster (12+ task definition, 1 service per backend)
6. ALB + Route 53 record cutover
7. Secrets Manager rotation lambda
8. 모니터링 (Managed Prometheus + Managed Grafana 또는 EC2 자체 그대로)

---

## 7. 참조

- 누적 결정: `migration/decisions/DECISIONS.md` (D-P8-03 ~ D-P8-06)
- 환경변수 표준: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- Phase 8 dev report: `docs/dev-reports/phase8-step-1-aws-readiness.md`
- Phase 8 readiness plan: `docs/migration/phase8/M-PHASE-8-readiness.md`
- ROADMAP: `ROADMAP.md`
