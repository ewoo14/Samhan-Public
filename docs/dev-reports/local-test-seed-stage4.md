# Stage 4 local-test seed — Phase 4/9 back-office 4 service

> Phase 4 (accounting-service) + Phase 9 W2/W3/W4 (groupware / notification / dashboard) 의 풀 수준 로컬 테스트
> seed 데이터 도입. branch = `feature/local-test-setup`. Stage 1 (partner 50 / product 100) + Stage 2
> (slip 100) + Stage 3 (partner-order 30 / arologis dispatch 20) 와 cross-service consistent 매핑 보존.

## 1. 산출물

| 파일 | 역할 | 분포 |
|---|---|---|
| `services/accounting-service/src/main/java/.../seed/JournalSeeder.java` | Journal 50 + Line ~110 | SLIP_ISSUE 30 / PAYMENT 10 / SGA 5 / ADJUSTMENT 5 |
| `services/accounting-service/src/main/resources/application.yml` | 토글 추가 | `app.accounting.seed-test-data` |
| `services/groupware-service/src/main/java/.../seed/GroupwareSeeder.java` | ApprovalLine 8 + Step 16 + Message 20 + Schedule 5 + Participant 9 | PENDING 3 / APPROVED 4 / REJECTED 1 |
| `services/groupware-service/src/main/resources/application.yml` | 토글 추가 | `app.groupware.seed-test-data` |
| `services/notification-service/src/main/java/.../seed/NotificationHistorySeeder.java` | NotificationRequest 50 + Log ~45 | PENDING 5 / SENT 35 / FAILED 5 / RETRYING 5 |
| `services/notification-service/src/main/resources/application.yml` | 토글 추가 | `app.notification.seed-test-data` |
| `services/dashboard-service/src/main/java/.../seed/DashboardSnapshotSeeder.java` | KpiSnapshot 135 + RealtimeStock 200 + SalesAggregate 150 + MV refresh | DAILY_SALES 100 / MONTHLY_SALES 5 / ORDER_COUNT 30 |
| `services/dashboard-service/src/main/resources/application.yml` | 토글 추가 | `app.dashboard.seed-test-data` |
| `docs/dev-reports/local-test-seed-stage4.md` | 본 dev-report | — |

## 2. 활성화 방법

### accounting-service
```bash
SPRING_PROFILES_ACTIVE=dev \
SAMHAN_ACCOUNTING_SEED_TEST_DATA=true \
./gradlew :services:accounting-service:bootRun
```

### groupware-service
```bash
SPRING_PROFILES_ACTIVE=dev \
SAMHAN_GROUPWARE_SEED_TEST_DATA=true \
./gradlew :services:groupware-service:bootRun
```

### notification-service
```bash
SPRING_PROFILES_ACTIVE=dev \
SAMHAN_NOTIFICATION_SEED_TEST_DATA=true \
./gradlew :services:notification-service:bootRun
```

### dashboard-service
```bash
SPRING_PROFILES_ACTIVE=dev \
SAMHAN_DASHBOARD_SEED_TEST_DATA=true \
./gradlew :services:dashboard-service:bootRun
```

이중 가드 — `@Profile("dev")` + `@ConditionalOnProperty` 모두 활성일 때만 시드 진입. 운영 / CI 에서는
모두 미설정 (default false) 으로 안전.

`@Order` 값으로 Stage 별 순서 지정:
- 40 = JournalSeeder (accounting)
- 50 = GroupwareSeeder
- 60 = NotificationHistorySeeder
- 70 = DashboardSnapshotSeeder (MV refresh 마지막)

## 3. 결정적 (deterministic) 매핑

본 Stage 4 seeder 는 모두 `samhan-seed:<type>:<key>` namespace 의 `UUID.nameUUIDFromBytes` 로 결정 UUID
를 도출한다. Stage 1/2/3 seeder 가 `@UuidGenerator` 로 random UUID 를 부여하더라도, cross-service
참조에서 동일한 비즈니스 키 (partnerCode / slipNo / loginId / productCode) 만 알면 본 Stage 4 seed 데이터와
join 가능하다. 향후 Stage 1/2/3 seeder 도 동일 namespace 로 정렬 권장 (별도 ticket).

### 3.1 Journal ↔ Stage 1 partner / Stage 2 slip / employee

| 필드 | 매핑 룰 |
|---|---|
| `journalNo` | `J-2026-00001` ~ `J-2026-00050` (zfill 5, spec 명시 포맷) |
| `id (UUID)` | `samhan-seed:journal:J-2026-NNNNN` |
| `journalDate` | 2026-01-01 ~ 2026-05-09 (3일 간격, 분포) |
| `sourceRefId` (SLIP_ISSUE 30건) | `samhan-seed:slip:<slipNo>` (slipNo = `2026/MM/DD-NNN` 결정 도출) |
| `partnerId` (line) | `samhan-seed:partner:P-2026-NNNN` |
| `postedBy` | accountant loginId 5명 라운드로빈 (leeseongmi/heoyujin/rahaeram/kimeunji/parkjisu) |
| `status` | DRAFT 5 (seq%10==1) / POSTED 40 / REVERSED 5 (seq%10==5) |

### 3.2 Groupware ↔ employee

| 필드 | 매핑 룰 |
|---|---|
| `requesterId` / `approverId` / `senderId` / `recipientId` / `ownerId` / `participantId` | `samhan-seed:employee:<loginId>` (16 employee) |
| ApprovalLine `id` | `samhan-seed:approval-line:approval-NN` |
| ApprovalStep `id` | `samhan-seed:approval-step:approval-NN:1\|2` |
| Message `id` | `samhan-seed:message:message-NN` |
| Schedule `id` | `samhan-seed:schedule:schedule-NN` |
| ScheduleParticipant `id` | `samhan-seed:schedule-participant:schedule-NN:P` |

### 3.3 Notification ↔ employee / partner

| 필드 | 매핑 룰 |
|---|---|
| `id` | `samhan-seed:notification:<channel>:<seq>` (NN zfill 2) |
| `recipientId` USER (30) | `samhan-seed:employee:<loginId>` |
| `recipientId` PARTNER (15) | `samhan-seed:partner:P-2026-NNNN` |
| `recipientId` EXTERNAL_PHONE (5) | `null` + recipientAddress = `010-XXXX-XXXX` |
| `payload` | `{"slipNo":"2026/05/DD-NNN","actor":"<loginId>"}` (JSONB) |
| Log `id` | `samhan-seed:notification-log:<requestId>:<attempt>` |

### 3.4 Dashboard ↔ Stage 1 product / partner

| 필드 | 매핑 룰 |
|---|---|
| KpiSnapshot `id` | `samhan-seed:kpi:<category>:<snapshotDate>` |
| RealtimeStock `id` | `samhan-seed:realtime-stock:<productCode>:<warehouse>` |
| RealtimeStock `productId` | `samhan-seed:product:M-2026-NNN` (Stage 1 product 100건) |
| RealtimeStock `warehouseCode` | WH-001 (수도권) / WH-002 (영남권) |
| SalesAggregate `id` | `samhan-seed:sales-aggregate:<date>:<partnerCode>` |
| SalesAggregate `partnerId` | `samhan-seed:partner:P-2026-NNNN` (VIP 5건: 1/2/5/7/11) |

## 4. 복식부기 invariant 검증

Spec 명시 가드 — `JournalSeeder` 는 모든 Journal 의 `sum(debit) == sum(credit)` 강제.

검증 layer 3중:

1. **도메인 가드** — `Journal.post(actorUserId)` 가 라인 합계 mismatch 시 `BusinessException(CONFLICT)` 발생.
   `applyStatus` 가 DRAFT 외 모든 분개에 대해 `post` 호출 → mismatch 시 즉시 fail-loud.
2. **DB constraint** — `ck_journal_lines_amount_xor` (V1 SQL line 122-124): `(debit > 0 AND credit = 0)
   OR (debit = 0 AND credit > 0)`. 한 라인에 차/대 동시 양수 금지.
3. **seeder 자체 가드** — `run` 마지막 grand-total 계산 후 `debitGrand == creditGrand` 확인 +
   per-journal `if (d.compareTo(c) != 0) throw IllegalStateException`. 운영 sanity.

각 type 별 차/대 분개 패턴 (모두 합계 일치):

| type | 차변 | 대변 |
|---|---|---|
| SLIP_ISSUE | 110 외상매출금 (net + vat) | 401 상품매출 (net) + 220 부가세예수금 (vat) |
| PAYMENT | 102 보통예금 (total) | 110 외상매출금 (total) |
| SGA | 801 급여 OR 814 통신비 (amount) | 102 보통예금 (amount) |
| ADJUSTMENT | 818 감가상각비 (amount) | 142 건물 (amount) |

> **참고** — V1 시드에 감가상각누계액 코드 (보통 142 누계 / 별도 코드) 가 미보유라 ADJUSTMENT 패턴은
> 자산 계정 (142 건물) 직접 차감으로 단순화. 정석은 누계액 충당금 계정. seed 데이터 한정 약식 처리이며
> 운영 분개는 향후 ChartOfAccount 확장 시 정정 (별도 backlog).

### 검증 expected output

`JournalSeeder` 콘솔 로그 마지막 줄:
```
JournalSeeder created 50 journals (skipped 0)
JournalSeeder 복식부기 invariant — sum(debit)=<X> sum(credit)=<X> OK
```

> spec 의 SLIP_ISSUE 라인은 3건 (수증 + 매출 + VAT) → 라인 총 ≈ 30*3 + 10*2 + 5*2 + 5*2 = **110 라인** 예상.

## 5. 한국 표준 계정과목 가드

`memory/project_korean_accounting.md` 의 한국 일반기업회계기준 표준 코드 (`110/220/401/801/814/818/102/142`) 만
사용한다. accounting-service V1 migration (`V1__init_accounting_service.sql`) 에 미리 시드된 ChartOfAccount
65 row 와 정확히 일치 — 외래키는 logical (DB FK 강제 X) 이지만 application service layer 가
ChartOfAccountRepository lookup 으로 leaf 검증.

## 6. 실행 순서 + 의존 그래프

Stage 1 → 2 → 3 → 4 순. Stage 4 내부 순서:

```
Stage 4 (back-office)
├─ accounting-service @Order(40) — Journal 50건 (SLIP/PAYMENT/SGA/ADJUSTMENT)
├─ groupware-service  @Order(50) — Approval/Message/Schedule
├─ notification-service @Order(60) — Notification + Log
└─ dashboard-service  @Order(70) — KPI / Stock / Aggregate + MV refresh
```

`@Order` 는 단일 Spring context 내 ordering 만 제어 (각 service 가 별도 process 이므로 process 간
순서는 `start-local-full.ps1` script 의 sequential bootRun 으로 보장).

## 7. UUID 비공개 가드 (memory/feedback_uuid_no_user_visibility)

본 Stage 4 의 모든 entity 는 사용자 노출 식별자 (`journalNo` / `partnerCode` / `productCode` /
`warehouseCode` / `loginId`) 와 별도로 UUID 를 PK 로 사용한다. seeder 의 본문 / payload / 메모는 사용자
식별자만 사용하고 UUID 직접 노출 X.

| entity | 노출 식별자 | UUID (비공개) |
|---|---|---|
| Journal | `journalNo` (J-2026-NNNNN) | `id` |
| ApprovalLine | `title` | `id` |
| Message | `body` | `id` |
| Schedule | `title` | `id` |
| NotificationRequest | `recipientAddress` (선택) | `id`, `recipientId` |
| KpiSnapshot | `category` + `snapshotDate` | `id` |
| RealTimeStock | `warehouseCode` | `id`, `productId` |
| SalesAggregate | `aggregateDate` | `id`, `partnerId` |

## 8. Materialized View refresh

`DashboardSnapshotSeeder.refreshMaterializedViews()` 는 시드 직후
`MaterializedViewRefreshService.refreshAll()` 호출:

```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_realtime_stock_summary;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_sales_daily_summary;
```

H2 환경 (test) 에서는 MATERIALIZED VIEW 미지원 → service 자체가 fail-soft (`tryRefresh` catch + warn log).
PostgreSQL 환경에서는 시드 + admin dashboard 즉시 조회 가능.

## 9. 컴파일 검증

```bash
./gradlew :services:accounting-service:compileJava \
          :services:groupware-service:compileJava \
          :services:notification-service:compileJava \
          :services:dashboard-service:compileJava
```

결과:
```
BUILD SUCCESSFUL
4 actionable tasks (compile)
```

`assemble` 단계까지 검증 시 `gradlew :services:accounting-service:assemble :services:groupware-service:assemble
:services:notification-service:assemble :services:dashboard-service:assemble` 도 green (Korean path JDK
trap 회피 — feedback_korean_path_jdk).

## 10. 다음 단계

1. Stage 1/2/3 seeder 가 random UUID 부여하는 부분을 `samhan-seed:<type>:<key>` namespace 결정 UUID
   로 정렬 (별도 backlog) — cross-service join 일관성 강화.
2. `start-local-full.ps1` 의 step 5 (psql row count 검증) 에 본 Stage 4 4 service 의 row count 추가:
   ```
   accounting_db: SELECT count(*) FROM journals;          -- 50
   accounting_db: SELECT count(*) FROM journal_lines;     -- 110
   groupware_db: SELECT count(*) FROM approval_lines;     -- 8
   groupware_db: SELECT count(*) FROM messages;           -- 20
   groupware_db: SELECT count(*) FROM schedules;          -- 5
   notification_db: SELECT count(*) FROM notification_requests; -- 50
   notification_db: SELECT count(*) FROM notification_logs;     -- ~45
   dashboard_db: SELECT count(*) FROM kpi_snapshots;            -- 135
   dashboard_db: SELECT count(*) FROM realtime_stocks;          -- 200
   dashboard_db: SELECT count(*) FROM sales_aggregates;         -- 150
   ```
3. ApprovalLine + Message API 화면 (groupware-service) 의 admin / 본인 inbox screenshot 첨부.
