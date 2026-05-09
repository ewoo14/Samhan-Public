# 풀 수준 로컬 테스트 인프라 — DevOps 산출물

> branch: `feature/local-test-setup`
> 작업일: 2026-05-09
> 범위: DevOps (env toggle / PowerShell 자동화 / docker-compose 검증 / README 보강)

---

## 1. 배경

Phase 10 진행 중 14 backend MSA + 5 client + 인프라 (postgres / redis / rabbitmq / es / minio / prometheus / grafana / nginx) 의 풀 수준 로컬 검증 절차가 산재되어 있어, 신규 합류자 / 회귀 테스트 / QA 대시보드 단축 흐름에서 **수동 셋업 시간 30분+** + **시드 변수 누락 사고** 가 반복 발생하였다.

본 슬라이스는:
1. 14 service 의 시드 toggle 을 단일 `.env` 파일로 일원화
2. 인프라 → 시드 → 14 service → health → row count 검증을 PowerShell 한 줄로 실행
3. docker-compose `init/*.sql` 의 service DB 누락 (logging_db / partner_auth_db / dc_config_db / partner_order_db) 을 보강

하여 셋업 시간을 **30분+ → 5분 이내** 로 단축한다.

---

## 2. 산출물

| 파일 | 신규/갱신 | 역할 |
| ---- | --------- | ---- |
| `infrastructure/env-templates/.env.dev-seed` | 신규 | 11개 SEED toggle env + Spring profile + Internal token 일원화 |
| `infrastructure/scripts/start-local-full.ps1` | 신규 | docker → env load → 14 service bootRun → health check → row count 검증 |
| `infrastructure/scripts/stop-local-full.ps1` | 신규 | actuator shutdown → job stop → process kill → docker compose down (-RemoveVolumes 옵션) |
| `infrastructure/postgres/init/01-create-databases.sql` | 갱신 | 12 → **16 DB** (logging_db / partner_auth_db / dc_config_db / partner_order_db 추가) |
| `infrastructure/postgres/init/02-extensions.sql` | 갱신 | 신규 4 DB 의 uuid-ossp / pgcrypto extension 보강 |
| `README.md` | 갱신 | "🛠 풀 수준 로컬 테스트 환경 구동" 섹션 신설 (단계별 / 시드 표 / 모니터링 화면 / 주의사항) |
| `docs/dev-reports/local-test-seed-devops.md` | 신규 | 본 보고서 |

---

## 3. `.env.dev-seed` 환경변수 일람

```bash
# Spring profile (전 service 공통)
SPRING_PROFILES_ACTIVE=dev

# 서비스 간 내부 인증 토큰 (전 service 공통)
SAMHAN_INTERNAL_TOKEN=dev-internal-token-change-me
INTERNAL_AUTH_TOKEN=dev-internal-token-change-me

# 시드 toggle (11개)
USER_SEED_ORG=true
PARTNER_SEED_TEST_DATA=true
PRODUCT_SEED_TEST_DATA=true
INVENTORY_SEED_TEST_DATA=true
SLIP_SEED_TEST_DATA=true
PARTNER_ORDER_SEED_TEST_DATA=true
AROLOGIS_SEED_TEST_DATA=true
ACCOUNTING_SEED_TEST_DATA=true
GROUPWARE_SEED_TEST_DATA=true
NOTIFICATION_SEED_TEST_DATA=true
DASHBOARD_SEED_TEST_DATA=true
```

### 매핑 — env 변수 → service seeder

| env | service | seeder bean | 가드 |
| --- | ------- | ----------- | ---- |
| `USER_SEED_ORG` | user-service | `OrgChartSeeder` (기존) | `@ConditionalOnProperty(value="app.user.seed-org", havingValue="true")` |
| `PARTNER_SEED_TEST_DATA` | partner-service | (W1 시드 backlog) | `partner.seed.test-data=true` |
| `PRODUCT_SEED_TEST_DATA` | product-service | `ProductSeedRunner` (기존) | `product.seed.test-data=true` |
| `INVENTORY_SEED_TEST_DATA` | inventory-service | (Phase 2 시드 backlog) | `inventory.seed.test-data=true` |
| `SLIP_SEED_TEST_DATA` | slip-service | (Phase 3 시드 backlog) | `slip.seed.test-data=true` |
| `PARTNER_ORDER_SEED_TEST_DATA` | partner-order-service | (M4 시드 backlog) | `partner-order.seed.test-data=true` |
| `AROLOGIS_SEED_TEST_DATA` | arologis-service | (W10-1 시드 backlog) | `arologis.seed.test-data=true` |
| `ACCOUNTING_SEED_TEST_DATA` | accounting-service | (Phase 4 65-row 표준 + 시드 backlog) | `accounting.seed.test-data=true` |
| `GROUPWARE_SEED_TEST_DATA` | groupware-service | (W2 시드 backlog) | `groupware.seed.test-data=true` |
| `NOTIFICATION_SEED_TEST_DATA` | notification-service | (W3 시드 backlog) | `notification.seed.test-data=true` |
| `DASHBOARD_SEED_TEST_DATA` | dashboard-service | (W4 시드 backlog — KPI snapshot bootstrap) | `dashboard.seed.test-data=true` |

> 본 PR 시점 — 11개 중 2개 (user / product) 만 seeder 구현 완료. 나머지 9개는 각 service 의 BE 슬라이스에서 추가 예정 (env 키 표준은 본 PR 에서 사전 확정).

---

## 4. `start-local-full.ps1` 실행 흐름

```
[1/6] 인프라 stack 기동              docker compose up -d (postgres + redis + rabbitmq + es + minio + monitoring)
                                    ↓ 30초 healthy 대기
[2/6] 시드 toggle 환경변수 로드      .env.dev-seed → $env:* (11 toggle + 2 token + 1 profile)
                                    ↓ Phase 8 chained-default — 15 service 별 *_DB_USER/PASSWORD 자동 매핑
[3/6] 14 service 의존순 기동          tier 0 → 7 sequential (eureka → auth → user/product/partner →
                                    inventory/accounting → slip/partner-order/arologis → groupware/notification →
                                    dashboard → api-gateway), 각 service Start-Job + /actuator/health 폴링
[4/6] service health 종합 요약       14 service 의 UP/DOWN Format-Table
[5/6] 시드 row count 검증            psql 11 query (employees:16 / partners:50 / products:100 / ...) Verdict (OK/LOW/SKIP)
[6/6] 사용 가이드                    마스터 로그인 / 모니터링 URL / 종료 명령어
```

옵션:
- `-SkipDocker` : 인프라 이미 떠 있음
- `-SkipServices` : 시드 검증만
- `-ServiceTimeoutSec <int>` : health check 최대 대기 (기본 60s)

---

## 5. `stop-local-full.ps1` 종료 절차

각 service 별 3-fallback graceful shutdown:

1. **`POST /actuator/shutdown`** 시도 (Spring Boot management.endpoint.shutdown.enabled 시)
2. **PowerShell Job stop** (`Stop-Job` + `Remove-Job` — start-local-full.ps1 가 띄운 background job)
3. **port 점유 process kill** (`Get-NetTCPConnection -LocalPort -State Listen` → `Stop-Process -Force`) — 최후 fallback

종료 순서는 의존 역순 (api-gateway → dashboard → ... → eureka).

옵션:
- `-RemoveVolumes` : `docker compose down -v` (postgres / redis / rabbitmq / es / minio volume 일체 삭제 — 시드 + 작업 데이터 소실)
- `-KeepDocker` : docker compose down 생략 (인프라 유지)

---

## 6. docker-compose DB 검증 + 보강

### 변경 전 (12 DB)

```
auth_db / user_db / product_db / inventory_db / slip_db / accounting_db /
partner_db / groupware_db / notification_db / dashboard_db / migration_db / arologis_db
```

### 변경 후 (16 DB) — Phase 6/9 신규 4 추가

```
+ logging_db        (Phase 1 logging-service — 기존 누락)
+ partner_auth_db   (Phase 6 M2 partner-auth-service)
+ dc_config_db      (Phase 6 M3 dc-config-service)
+ partner_order_db  (Phase 6 M4 partner-order-service)
```

### 검증 명령

```powershell
docker compose -f infrastructure/docker-compose.yml down -v
docker compose -f infrastructure/docker-compose.yml up -d postgres
docker exec samhan-postgres psql -U samhan -l
# → 16 DB 출력 확인
```

---

## 7. 주의사항 / 향후 작업

### 즉시 적용 가드

- **production 침입 방지** — `.env.dev-seed` 의 모든 toggle 은 production `.env.prod` 에 절대 포함 금지. `@Profile("dev")` + `@ConditionalOnProperty` 이중 가드.
- **PowerShell UTF-8** — 본 PR 의 신규 .ps1 / .env / .sql / .md 일체 UTF-8 (BOM X) 으로 작성. `Set-Content` 기본값 UTF-16 LE BOM → 한글 주석 깨짐 회피 (메모리 가드 `feedback_powershell_utf8_writes.md`).
- **idempotency** — seeder 는 모두 `existsBy*` 검증 후 insert. 재실행 시 row 중복 추가 안 됨.

### Phase 10 잔여 backlog (본 PR 범위 외)

11개 SEED toggle 중 9개는 각 service seeder 미구현 (env 표준만 본 PR 에서 사전 확정):

1. partner-service `PartnerSeeder` — 50건 한국 HVAC 협력사
2. inventory-service `InventoryBalanceSeeder` — 100 product × 2 warehouse
3. slip-service `SlipSeeder` — 11 status 균등 분포 100건
4. partner-order-service `OrderSeeder` — 30건 confirm 흐름 시나리오
5. arologis-service `DispatchSeeder` — 20건 Mock DriverMatcher
6. accounting-service `AccountingSlipSeeder` — 30 회계 전표
7. groupware-service `GroupwareSeeder` — 결재선 5 / 메신저 10 / 일정 20
8. notification-service `ChannelSeeder` — FCM/SES/Aligo 채널 매트릭스
9. dashboard-service `KpiBootstrapSeeder` — KPI snapshot + materialized view 초기 refresh

### Phase 11 AWS cutover 시점 가드

- `.env.prod` 작성 시 본 11개 변수 일체 미포함 (default false)
- AWS Secrets Manager rotation lambda 가 `SAMHAN_INTERNAL_TOKEN` rotate 시 본 .env.dev-seed 와 충돌 없음 (DEV 전용)
- RDS 첫 부팅 시 `init/*.sql` 미실행 (RDS 는 docker entrypoint 미적용) — 별도 `flyway:migrate` 1회 실행 필수

---

## 8. 사용 시나리오

### 신규 합류자 (5분 셋업)

```powershell
git clone <repo>
cd Samhan Public
.\infrastructure\scripts\start-local-full.ps1
# → 5분 후 마스터 로그인 가능 (CEO 김미선)
```

### QA 회귀 (clean state)

```powershell
.\infrastructure\scripts\stop-local-full.ps1 -RemoveVolumes
.\infrastructure\scripts\start-local-full.ps1
# → 모든 시드 1회 재생성, idempotent 검증
```

### 디버깅 (인프라 유지, service 만 재기동)

```powershell
.\infrastructure\scripts\stop-local-full.ps1 -KeepDocker
.\infrastructure\scripts\start-local-full.ps1 -SkipDocker
```

---

## 9. 변경 파일 요약

```
infrastructure/env-templates/.env.dev-seed         | (신규)
infrastructure/scripts/start-local-full.ps1        | (신규)
infrastructure/scripts/stop-local-full.ps1         | (신규)
infrastructure/postgres/init/01-create-databases.sql | (갱신, +4 DB)
infrastructure/postgres/init/02-extensions.sql     | (갱신, +6 \c stanza)
README.md                                          | (갱신, "🛠 풀 수준 로컬 테스트 환경 구동" 섹션 신설)
docs/dev-reports/local-test-seed-devops.md         | (신규, 본 보고서)
```
