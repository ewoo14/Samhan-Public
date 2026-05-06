# M-ENV-STANDARDIZATION — 환경변수 표준화 (12 + 4 service)

본 문서는 14 backend service 의 환경변수 명명 표준 + secrets/config 분리 + AWS Secrets Manager 마이그레이션 가능성을 정리한다.

---

## 1. 현재 환경변수 명명 분석

### 1-1. 현재 패턴 (12 service grep 결과)

각 service `application.yml` `\$\{[A-Z_]+:` 패턴 grep:

| 카테고리 | 환경변수 | 사용 service 수 | 패턴 |
|---|---|---|---|
| DB host | `DB_HOST` | 9 | 공통 (eureka / api-gateway / logging 제외) |
| DB port | `DB_PORT` | 9 | 공통 |
| DB user | `DB_USER` | 9 | 공통 |
| DB password | `DB_PASSWORD` | 9 | 공통 |
| DB name | `DB_NAME` | 9 | service 별 default 다름 (`auth_db`, `user_db`, ...) |
| Eureka URL | `EUREKA_URL` | 11 | 공통 (eureka-server 자체 제외) |
| Eureka peer | `EUREKA_PEER_URL` | 1 | eureka-server 만 |
| Eureka hostname | `EUREKA_INSTANCE_HOSTNAME` | 1 | eureka-server 만 |
| JWT secret | `JWT_SECRET` | 3 | api-gateway / auth-service / partner-auth-service |
| Internal token | `INTERNAL_AUTH_TOKEN` | 6 | auth-service / dc-config-service / accounting / user / inventory / product / slip |
| Internal token (variant) | `INTERNAL_TOKEN` | 1 | partner-order-service (불일치) |
| Service URL | `<NAME>_HOST` | 5 | partner-order-service 가 5개 service 호출 |
| Service URL (variant) | `<NAME>_URL` | 1 | partner-auth-service `DC_CONFIG_URL` (불일치) |
| RabbitMQ | `RABBIT_HOST` / `RABBIT_PORT` / `RABBIT_USER` / `RABBIT_PASSWORD` | 1 | logging-service 만 |
| Elasticsearch | `ES_URI` | 1 | logging-service 만 |
| SMS Aligo | `ALIGO_*` | 1 | slip-service 만 |
| SMS vendor | `SMS_VENDOR` | 1 | slip-service 만 |
| Public URL | `PUBLIC_BASE_URL` | 1 | slip-service 만 |
| Google Sheets | `GOOGLE_SHEETS_*` / `GOOGLE_SERVICE_ACCOUNT_KEY` | 1 | product-service 만 |
| Seed dry-run | `SEED_DRY_RUN_MODE` / `SEED_REPORT_DIR` / `SEED_SHEET_DIR` | 1 | product-service 만 |
| Product sync | `PRODUCT_SYNC_SCHEDULING_ENABLED` / `PRODUCT_SYNC_CRON` | 1 | product-service 만 |
| User seed | `USER_SEED_ORG` | 1 | user-service 만 |
| Warehouse UUID | `WAREHOUSE_UUID_*` | 1 | slip-service 만 |

### 1-2. 검출된 불일치 항목 (3건)

| 항목 | 현재 | 권장 |
|---|---|---|
| internal token | `INTERNAL_AUTH_TOKEN` (6) vs `INTERNAL_TOKEN` (1) | `INTERNAL_AUTH_TOKEN` 통일 (권장) |
| service URL | `DC_CONFIG_HOST` (1, partner-order) vs `DC_CONFIG_URL` (1, partner-auth) | `<NAME>_HOST` (host only) 또는 `<NAME>_URL` (full URL) 통일 — 추후 결정 |
| ALIGO sender phone | `ALIGO_SENDER_PHONE` 의 default = `01000000000` | placeholder 그대로 OK (test) |

> 본 슬라이스 = docs only, 코드 변경 X. 통일 작업은 별도 슬라이스 위임.

---

## 2. 환경변수 표준 (권장 패턴)

### 2-1. 공통 환경변수 (모든 service 공유)

| 환경변수 | 용도 | 구분 |
|---|---|---|
| `DB_HOST` | PostgreSQL host | secret |
| `DB_PORT` | PostgreSQL port | config |
| `DB_USER` | PostgreSQL user | secret |
| `DB_PASSWORD` | PostgreSQL password | **secret (rotation)** |
| `DB_NAME` | PostgreSQL database | config (service 별 다름) |
| `EUREKA_URL` | Eureka registry URL | config |
| `JWT_SECRET` | JWT HS256 secret (32+ bytes) | **secret (rotation)** |
| `INTERNAL_AUTH_TOKEN` | service-to-service token | **secret (rotation)** |
| `SPRING_PROFILES_ACTIVE` | Spring profile | config (`prod` / `staging` / `dev` / `local`) |

### 2-2. service 별 prefix 권장 (Phase 9 신규 service 부터)

| 패턴 | 예시 | 용도 |
|---|---|---|
| `SAMHAN_<SERVICE>_<KEY>` | `SAMHAN_NOTIFICATION_SLACK_WEBHOOK` | service 한정 변수 (vendor lock-in 회피 + 식별성) |
| `<EXTERNAL_NAME>_<KEY>` | `ALIGO_API_KEY`, `GOOGLE_SHEETS_SHEET_ID` | 외부 vendor — vendor 명 prefix |
| `<INFRA>_<HOST/PORT/USER/PASSWORD>` | `DB_HOST`, `RABBIT_HOST`, `S3_ENDPOINT` | backing service — infra 명 prefix |

### 2-3. service 호출 URL 통일 (Phase 9 적용)

```yaml
# 권장 — full URL (스킴 + host + port 일관)
internal:
  product-service-url: ${PRODUCT_SERVICE_URL:http://product-service:8084}
  inventory-service-url: ${INVENTORY_SERVICE_URL:http://inventory-service:8085}
```

> 현재 partner-order-service 의 `<NAME>_HOST` 패턴은 보존 (legacy compat). Phase 9 신규 service 부터 `<NAME>_SERVICE_URL` 적용.

---

## 3. secrets vs config 분리

### 3-1. secrets (rotation 의무)

| 환경변수 | 권장 위치 (현재) | 권장 위치 (AWS) |
|---|---|---|
| `DB_PASSWORD` | `.env` (gitignored) | AWS Secrets Manager (rotation lambda — 30 days) |
| `JWT_SECRET` | `.env` (gitignored) | AWS Secrets Manager (rotation 분기별) |
| `INTERNAL_AUTH_TOKEN` | `.env` (gitignored) | AWS Secrets Manager (rotation 분기별) |
| `RABBIT_PASSWORD` | `.env` (gitignored) | AWS Secrets Manager (rotation 분기별) |
| `ALIGO_API_KEY` / `ALIGO_USER_ID` | `.env` (gitignored) | AWS Secrets Manager |
| `GOOGLE_SERVICE_ACCOUNT_KEY` (file path) | `/etc/samhan/sa-key.json` (host) | AWS Secrets Manager (binary secret) 또는 IAM role |

### 3-2. config (rotation X)

| 환경변수 | 권장 위치 (현재) | 권장 위치 (AWS) |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `.env` 또는 docker-compose | Parameter Store (`/samhan/<env>/db/host`) |
| `EUREKA_URL` | `.env` 또는 docker-compose | Parameter Store |
| `<NAME>_HOST` / `<NAME>_SERVICE_URL` | `.env` 또는 docker-compose | Parameter Store 또는 Cloud Map |
| `PUBLIC_BASE_URL` | `.env` | Parameter Store |
| `WAREHOUSE_UUID_*` | `.env` | Parameter Store |
| `PRODUCT_SYNC_CRON` / `PRODUCT_SYNC_SCHEDULING_ENABLED` | `.env` | Parameter Store |
| `SEED_DRY_RUN_MODE` | `.env` (default true) | Parameter Store (production = false) |

### 3-3. .env.example 일관성 (Phase 9 가드)

각 service 가 `.env.example` 보유 의무 (현재 부분만 보유). 표준 patterns:

```bash
# === backing services ===
DB_HOST=localhost
DB_PORT=5432
DB_USER=samhan
DB_PASSWORD=CHANGE_ME_LOCAL_ONLY
DB_NAME=<service>_db

EUREKA_URL=http://localhost:8761/eureka/

# === secrets (rotation) ===
JWT_SECRET=CHANGE_ME_32BYTES_MIN
INTERNAL_AUTH_TOKEN=CHANGE_ME_LOCAL_ONLY
```

---

## 4. AWS Secrets Manager 마이그레이션 가능성 (Phase 10)

### 4-1. Spring Cloud AWS Secrets Manager integration

```yaml
# Phase 10 적용 패턴
spring:
  config:
    import:
      - aws-secretsmanager:/samhan/<env>/<service>/
```

→ 환경변수 명 그대로 유지하면서 `application.yml` 의 `${DB_PASSWORD}` 등이 Secrets Manager 에서 자동 resolve.

### 4-2. IAM role 활용 (key file 회피)

- `GOOGLE_SERVICE_ACCOUNT_KEY` 같은 file path 의존 secret = AWS Secrets Manager binary secret 으로 전환 가능
- 또는 ECS task IAM role + Workload Identity Federation 활용

### 4-3. 현재 단계 행동 (Phase 8)

- 환경변수 명명 그대로 유지 = AWS 마이그레이션 시 `application.yml` 변경 X
- 새 service 추가 시 본 표준 적용 의무

---

## 5. 후속 작업 (Phase 10 시점)

| 작업 | 산출 |
|---|---|
| 12 service `INTERNAL_TOKEN` → `INTERNAL_AUTH_TOKEN` 통일 | application.yml 정정 |
| 12 service `<NAME>_HOST` → `<NAME>_SERVICE_URL` 통일 | application.yml 정정 |
| 모든 service `.env.example` 의무 추가 | 12 file 신규 |
| AWS Secrets Manager integration | spring-cloud-aws-starter 도입 |
| Parameter Store integration | spring-cloud-aws-starter 도입 |

> 본 슬라이스 = docs only, 코드 변경 X. 통일 작업은 Phase 10 또는 별도 슬라이스 위임.

---

## 6. 참조

- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- Phase 8 dev report: `docs/dev-reports/phase8-step-1-aws-readiness.md`
- 누적 결정: `migration/decisions/DECISIONS.md` (D-P8-03 ~ D-P8-06)
