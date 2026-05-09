# 로컬 풀-수준 테스트 시나리오 가이드 (local-test-seed-data)

> **branch**: `feature/local-test-setup`
> **목적**: SamhanLogis 14 backend MSA 의 시드 데이터 + end-to-end 시나리오 검증 plan
> **선행 산출물**: `infrastructure/env-templates/.env.dev-seed` (시드 toggle 일괄 활성화)
> **대상**: 로컬 검증 → PR QA → Phase 11 cutover dry-run
> **본 문서 범위**: 시나리오 명세 + curl/HTTP 예제 + 기대값 + psql 검증 SQL.
> **본 문서 비범위**: 단위/IT 테스트 Java 코드 (BE 팀 위임 — `feedback_multi_agent_team_pattern.md`).

---

## 0. QA 정책 — 본 문서의 자체 산출 vs 위임

회고 가드 `feedback_multi_agent_team_pattern.md` + `feedback_pm_integration_build_check.md` 준수:

| 산출물 | 담당 | 본 문서에서의 위치 |
|---|---|---|
| 시나리오 7건 명세 (curl + HTTP + 기대값) | **QA 자체** | `scenarios/01-07-*.md` |
| 도메인 정합성 SQL 모음 (FK 무결성, 복식부기 등) | **QA 자체** | `domain-integrity-check.md` |
| 단위 테스트 / IT Java 코드 | **BE 팀 위임** | (본 문서 범위 외) |
| 시드 데이터 Java seeder 구현 | **BE 팀 위임** (본 PR 의 후속) | toggle 명세만 §1 에 |
| 14 service .env profile 표 | **DevOps 팀 위임** | `.env.dev-seed` 위치 인용만 |
| start-local-full PowerShell 스크립트 | **DevOps 팀 위임** | §3 에 호출 패턴만 |
| FE Cypress E2E 시나리오 | **FE 팀 위임** (별도 슬라이스) | (본 문서 범위 외) |

QA 의 IT Java 직접 작성 금지 — PR #16/17/21 회고 (PM 통합 단계에서 BE Layer 4 시그니처 미스매치 사고 방지).

---

## 1. 시드 데이터 toggle 매트릭스

본 시나리오는 `infrastructure/env-templates/.env.dev-seed` 의 11 toggle 이 모두 `true` 인 상태를 기본 가정.

| # | service | env 변수 | 시드 row 수 (기대) | 기존 구현 위치 |
|---|---|---|---|---|
| 1 | user-service | `USER_SEED_ORG=true` | 16 employees + 5 departments | `OrgChartSeeder.java` (구현됨) |
| 2 | partner-service | `PARTNER_SEED_TEST_DATA=true` | 50 partners | (BE 팀 신규 — `PartnerTestDataSeeder.java` 예정) |
| 3 | product-service | `PRODUCT_SEED_TEST_DATA=true` | 100 products + N categories | (BE 팀 신규) |
| 4 | inventory-service | `INVENTORY_SEED_TEST_DATA=true` | 200 row (100 product × 2 warehouse) + 2 warehouses | (BE 팀 신규) |
| 5 | slip-service | `SLIP_SEED_TEST_DATA=true` | 100 slips (11 status 균등 분포 + 그 중 5건 driverPhone 묶음) | (BE 팀 신규) |
| 6 | partner-order-service | `PARTNER_ORDER_SEED_TEST_DATA=true` | 30 orders + outbox | (BE 팀 신규) |
| 7 | arologis-service | `AROLOGIS_SEED_TEST_DATA=true` | 20 dispatches (10 vehicles + 50 stops) + 5 drivers | (BE 팀 신규) |
| 8 | accounting-service | `ACCOUNTING_SEED_TEST_DATA=true` | 65 chart_of_accounts (V1) + 50 journals (POSTED 40 / DRAFT 5 / REVERSED 5) | V1 65 row 시드됨, journal seeder 신규 |
| 9 | groupware-service | `GROUPWARE_SEED_TEST_DATA=true` | 결재선 5 / 메신저 10 / 일정 20 | (BE 팀 신규) |
| 10 | notification-service | `NOTIFICATION_SEED_TEST_DATA=true` | 채널 매트릭스 (FCM/SES/Aligo) | (BE 팀 신규) |
| 11 | dashboard-service | `DASHBOARD_SEED_TEST_DATA=true` | KPI 30일 × 6 카테고리 + 200 realtime_stocks + 150 sales_aggregates | (BE 팀 신규) |

> **idempotency 규약** — 모든 seeder 는 `existsBy*` / `count() > 0` 가드로 재실행 시 row 중복 추가 금지.
> 본 가드는 `OrgChartSeeder` 의 패턴 (`employeeRepository.existsByLoginId`) 을 모든 신규 seeder 가 답습.

---

## 2. 시나리오 색인 (7건)

각 시나리오 상세는 `scenarios/` 하위 파일에 분리 작성.

| # | 시나리오 제목 | 파일 | 요약 |
|---|---|---|---|
| 1 | 마스터 로그인 + 기본 화면 검증 | `scenarios/01-master-login.md` | CEO 로그인 → JWT → 직원 list / row count 검증 |
| 2 | End-to-end 슬립 라이프사이클 | `scenarios/02-slip-lifecycle.md` | DRAFT → SAVED → SENT → ACCEPTED → ... → CONFIRMED + 자동 분개 |
| 3 | 모바일 서명 (delivery batch) | `scenarios/03-mobile-signature.md` | 5건 묶음 batch + driver 모바일 서명 + APP source |
| 4 | 거래처 주문 → 슬립 자동 발행 | `scenarios/04-partner-order-publish.md` | partner draft → confirm → slip 발행 + idempotency |
| 5 | 회계 보고서 (이카운트 17 보고서 매핑) | `scenarios/05-accounting-reports.md` | 분개장 / 시산표 / 계정과목 트리 |
| 6 | Arologis 카카오톡 배차 파싱 | `scenarios/06-arologis-dispatch.md` | 카톡 메시지 → vehicle/stop 자동 생성 + driver 매칭 |
| 7 | 대시보드 + 대량 데이터 | `scenarios/07-dashboard-bulk.md` | KPI / realtime stock / sales aggregate 200+ row |

---

## 3. 사전 준비 (모든 시나리오 공통)

### 3.1 인프라 기동

```powershell
cd C:\dev\SamhanLogis\infrastructure
docker compose up -d
docker compose ps   # 7 service all healthy 확인
```

### 3.2 시드 toggle 적용

`.env.dev-seed` 의 11 toggle 을 환경변수로 export (또는 IDE Run Configuration / start script).

```powershell
# 권장 — DevOps 팀이 발행 예정 PowerShell 스크립트
.\infrastructure\scripts\start-local-full.ps1
```

### 3.3 14 service 기동 순서

| 단계 | service | port | 의존성 |
|---|---|---|---|
| 1 | eureka-server | 8761 | (없음) |
| 2 | dc-config-service | 8770 | eureka |
| 3 | logging-service | 8780 | eureka |
| 4 | auth-service | 8081 | eureka, postgres(auth_db) |
| 5 | user-service | 8082 | eureka, auth-service, postgres(user_db) |
| 6 | partner-service | 8088 | eureka, postgres(partner_db) |
| 7 | partner-auth-service | 8089 | eureka, partner-service |
| 8 | product-service | 8083 | eureka, postgres(product_db) |
| 9 | inventory-service | 8084 | eureka, product-service, postgres(inventory_db) |
| 10 | slip-service | 8085 | eureka, partner/product/inventory, postgres(slip_db) |
| 11 | partner-order-service | 8086 | eureka, partner-auth/partner/product/inventory/slip, postgres(partner_order_db) |
| 12 | accounting-service | 8087 | eureka, postgres(accounting_db) |
| 13 | groupware-service | 8090 | eureka, postgres(groupware_db) |
| 14 | notification-service | 8091 | eureka, rabbitmq, postgres(notification_db) |
| 15 | dashboard-service | 8092 | eureka, partner/inventory/slip, postgres(dashboard_db), redis |
| 16 | arologis-service | 8093 | eureka, slip/partner/notification, postgres(arologis_db) |
| 17 | api-gateway | 8080 | eureka, 모든 backend |

> 16+1 process 가 fully ready 후 시나리오 진행. eureka registration ready 약 60초.

### 3.4 row count 사전 검증 (시나리오 진입 가드)

```sh
docker exec -it samhan-postgres psql -U samhan -d user_db        -c "SELECT count(*) FROM employees;"          # 16 (CEO 김미선 외 15)
docker exec -it samhan-postgres psql -U samhan -d user_db        -c "SELECT count(*) FROM departments;"        # 5
docker exec -it samhan-postgres psql -U samhan -d partner_db     -c "SELECT count(*) FROM partners WHERE NOT is_deleted;"            # 50
docker exec -it samhan-postgres psql -U samhan -d product_db     -c "SELECT count(*) FROM products WHERE NOT is_deleted;"            # 100
docker exec -it samhan-postgres psql -U samhan -d inventory_db   -c "SELECT count(*) FROM warehouses WHERE NOT is_deleted;"          # 2
docker exec -it samhan-postgres psql -U samhan -d slip_db        -c "SELECT count(*) FROM slips WHERE NOT is_deleted;"               # 100
docker exec -it samhan-postgres psql -U samhan -d accounting_db  -c "SELECT count(*) FROM chart_of_accounts WHERE NOT is_deleted;"   # 65 (V1 표준)
docker exec -it samhan-postgres psql -U samhan -d accounting_db  -c "SELECT count(*) FROM journals WHERE NOT is_deleted;"            # 50
docker exec -it samhan-postgres psql -U samhan -d arologis_db    -c "SELECT count(*) FROM dispatches WHERE NOT is_deleted;"          # 20
docker exec -it samhan-postgres psql -U samhan -d dashboard_db   -c "SELECT count(*) FROM kpi_snapshots WHERE NOT is_deleted;"       # 180 (30 × 6)
```

위 값과 다르면 시드 toggle 누락 또는 seeder 구현 누락 — 시나리오 진입 전 BE 팀 alert.

### 3.5 인증 헤더 패턴 (모든 admin endpoint 공통)

API Gateway 가 JWT 검증 후 backend service 로 다음 헤더 주입.
백엔드는 `X-User-Role` 기반 `@PreAuthorize` 가드 적용.

| 헤더 | 의미 | 예시 |
|---|---|---|
| `Authorization` | `Bearer <jwt>` (gateway 경계만) | `Bearer eyJhbGciOi...` |
| `X-User-Id` | UUID (employees.id) | `00000000-0000-0000-0000-000000000001` |
| `X-User-Role` | MASTER / MANAGER / SALES / WAREHOUSE / INVENTORY / ACCOUNTANT / DRIVER / PARTNER | `MASTER` |
| `X-Partner-Code` | 거래처 호출 시만 | `P0001` |
| `X-Internal-Token` | service-to-service 호출 시 | `dev-internal-token-change-me` |

UUID 비공개 가드 — 응답 본문 내 UUID 는 화면 노출 금지 (`feedback_uuid_no_user_visibility.md`).
사용자 노출 식별자는 `slipNo` / `partnerCode` / `warehouseCode` / `modelName` / `journalNo` / `driverCode` 사용.

---

## 4. 도메인 정합성 검증 checklist

본 표는 시나리오 1~7 완료 후 일괄 검증.
상세 SQL 은 `domain-integrity-check.md` 참조.

| # | Check 항목 | 기대값 |
|---|---|---|
| C1 | `slips.partner_id` → `partners.id` FK 정합성 | 모든 slip 의 partner_id 가 활성 partner 매칭 |
| C2 | `slip_lines.product_id` → `products.id` cross-DB 정합성 | 100% 매칭 (Feign 또는 dev-tool 검증) |
| C3 | `journal_lines` 복식부기 차/대 합계 일치 | mismatch row = 0 |
| C4 | `journal_lines.account_code` 한국 표준 65 코드 한정 | 비표준 코드 row = 0 |
| C5 | `delivery_batches` (driver_phone, batch_date) partial unique | 활성 row 중복 = 0 |
| C6 | seeder 2회 실행 후 row count 동일 (idempotency) | row count 동일 |
| C7 | `slip_publish_audit.idempotency_key` 동일 키 → slipNo 동일 | replay 검증 |
| C8 | `slips.requester_id` ↔ `employees` 매칭 (cross-DB) | 100% (또는 `system`) |
| C9 | `vehicles.assigned_driver_id` → `drivers.id` (arologis) | 매칭 100% |
| C10 | `kpi_snapshots` (snapshot_date, category) partial unique | 활성 row 중복 = 0 |

각 check 의 SQL 은 `domain-integrity-check.md` 의 §1~§10 에 매핑.

---

## 5. 진입 / 종료 기준

### 5.1 진입 기준 (DoR — Definition of Ready)

- [ ] 14 service ready (eureka registration 완료)
- [ ] 11 시드 toggle row count 표 (§3.4) 통과
- [ ] 인프라 7개 컨테이너 healthy
- [ ] `docs/qa/local-test-seed-data/scenarios/*.md` 7건 review 완료

### 5.2 종료 기준 (DoD — Definition of Done)

- [ ] 시나리오 1~7 모두 happy path 통과 (각 시나리오의 §종료 기준 표 만족)
- [ ] §4 정합성 check 10건 모두 0 mismatch
- [ ] QA 결과 스크린샷 1장 이상 인라인 (`feedback_pr_qa_screenshots.md`)
- [ ] dev-report `docs/dev-reports/local-test-seed-data.md` 누적 완료 (BE/FE/QA 합본)

---

## 6. 회귀 가드 (기존 PR 체크 보존)

본 시나리오 PR 머지 시 기존 PR 의 다음 가드를 재검증.

| PR | 가드 항목 |
|---|---|
| PR #16/17/21 회고 | PM 통합 풀빌드 — `gradle assemble` × 14 service all green |
| PR #18 회고 | UUID 비공개 — 응답에 slip.id / employee.id UUID 노출 금지 |
| PR #21 회고 | 인쇄 양식 디자인 반복 — 본 시나리오는 인쇄 X (해당 없음) |
| PR #34 회고 | PR CI 모니터링 — `gh pr checks --watch` 자동 시작 |
| PR #80/85 회고 | 본 시나리오 PR 에 README/ROADMAP/DECISIONS 동기 갱신 |
| PR #99 회고 | arologis driver-app 서명 → slip-service 양쪽 저장 (slipBridged 플래그) |

---

## 7. 참고 자료

- `infrastructure/env-templates/.env.dev-seed` — 시드 toggle 일괄 정의
- `services/user-service/src/main/java/com/samhanair/logis/user/seed/OrgChartSeeder.java` — seeder 구현 패턴 reference
- `services/accounting-service/src/main/resources/db/migration/V1__init_accounting_service.sql` — 한국 표준 65 계정과목 시드
- `docs/qa/accounting-slice-A/qa-report.md` — QA 시나리오 명세 패턴 reference
- `feedback_multi_agent_team_pattern.md` — QA 의 IT 위임 원칙
- `feedback_uuid_no_user_visibility.md` — UUID 비공개 가드
- `feedback_korean_accounting.md` — 한국 일반기업회계기준 표준 계정과목 코드
