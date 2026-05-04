# QA Report — Phase 4 Accounting Service Slice A (A1+A2 통합 MVP)

> **PR 후보**: PR #27 — accounting-service (port 8087, accounting_db) MVP
> **작성**: 2026-05-04 QA agent (5-team parallel 디스패치)
> **Plan 인용**: `docs/dev-reports/accounting-slice-A/plan.md` §1~§9
> **Designer 인용**: `docs/design/accounting-slice-A/{README,wireframes,ux-flow}.md`
> **사용자 확정** (Plan §7): 수동 분개만, ACCOUNTANT/MASTER 권한, AR/AP/자동분개 deferred

본 문서는 **회계 서비스 MVP** 의 QA 산출물입니다. IT (Integration Test) 시나리오 14건을 BE 팀에 위임하며, QA 는 **시나리오 명세 + 권한 매트릭스 + UUID 가드 + 회귀 가드 + fixtures.http** 를 책임집니다.

---

## 0. QA 정책 — 위임 vs 자체 산출

회고 가드 `feedback_multi_agent_team_pattern.md` 준수:

| 산출물 | 담당 | 본 보고서에서의 위치 |
|---|---|---|
| IT Java 코드 (실제 `.java` 파일) | **BE 팀 위임** | §2 시나리오 표만 명세 |
| 단위 테스트 | BE 팀 | (본 보고서 범위 외) |
| `fixtures.http` (수동 검증 시나리오) | **QA 자체** | `services/accounting-service/src/test/resources/fixtures.http` |
| 권한 매트릭스 7-tier | **QA 자체** | §3 |
| BE Layer 4 시그니처 가정 표 | **QA 자체** (BE 통합 검증 base) | §4 |
| UUID 비공개 가드 verify | **QA 자체** | §5 |
| 회귀 가드 (기존 7 서비스) | **QA 자체** | §6 |
| 한국 표준 계정 시드 검증 | **QA 자체** | §7 |

QA 의 IT Java 직접 작성 금지 — PR #16/17/21 회고 (PM 통합 단계에서 BE Layer 4 시그니처 미스매치 사고 방지).

---

## 1. 테스트 환경 & 가드

### 1.1 Testcontainers 패턴 (slip-service 답습)

- `AccountingAbstractPostgresIT` 신규 — `slip-service.it.AbstractPostgresIT` 동일 구조
  - 싱글턴 컨테이너 (`postgres:16-alpine` + `accounting_db`)
  - `DockerAvailableCondition` skip 가드 (`feedback_testcontainers_windows_docker.md`)
  - `@DynamicPropertySource` 로 datasource / flyway / eureka.disabled / internal.token 주입

### 1.2 외부 client @MockBean 의무 (`feedback_it_mockbean_external_clients.md`)

본 슬라이스는 **외부 RestClient 없음** (Plan Q1 — slip 자동 분개 A3 deferred, Q3 — Partner 연계 A4 deferred). 단,

- `internal.token` 은 등록 (X-Internal-Token 답습 — A3 진입 대비)
- BE 팀 IT 작성 시 신규 client 추가하면 즉시 `@MockBean` + `Mockito.lenient()` 의무

### 1.3 Korean Path JDK Trap (`feedback_korean_path_jdk.md`)

- 한글 경로 (`C:\dev\삼한*`) JDK 17 + `gradle test` 시 fork JVM crash → IT skip 됨
- 권장: `gradle :services:accounting-service:assemble` 로컬 빌드, IT 는 CI 에서만 강제

---

## 2. IT 시나리오 명세 (14건 — BE 팀 위임)

### 2.1 ChartOfAccountIT (3건) — 한국 표준 계정과목 V1 시드 검증

| # | 메서드명 | 입력 | 기대 status | jsonPath assertion |
|---|---|---|---|---|
| 1 | `findAll_returnsKoreanStandardChart50Plus` | `GET /accounting/accounts` (no filter) | 200 | `$.data.length()` >= 50, `$.data[?(@.code=='101')].name` == `현금`, `$.data[?(@.code=='805')].name` == `임차료` |
| 2 | `findAll_orderByCodeAsc` | `GET /accounting/accounts` | 200 | `$.data[0].code` < `$.data[1].code` (lexicographic, 6-digit) — 코드 오름차순 정렬 검증 |
| 3 | `findByCategory_groupsCorrectly` | `GET /accounting/accounts?category=ASSET` | 200 | `$.data[*].code` 모두 `1` 시작, group=ASSET 7-그룹 분류 검증 (자산 100-163 범위) |

### 2.2 JournalControllerIT (9건) — 권한 + 라이프사이클 + 검증

| # | 메서드명 | 입력 | 기대 status | jsonPath assertion |
|---|---|---|---|---|
| 4 | `createJournal_accountantRole_returns201_statusDraft` | `POST /accounting/journals` + `X-User-Role: ACCOUNTANT` + 2-line body (차/대 합계 일치) | 201 | `$.data.status` == `DRAFT`, `$.data.journalNo` matches `^\d{8}-\d+$`, `$.data.lines.length()` == 2 |
| 5 | `createJournal_managerRole_returns403` | `POST /accounting/journals` + `X-User-Role: MANAGER` | 403 | `$.error.code` == `FORBIDDEN` (Q9 — MANAGER 제외 명시) |
| 6 | `createJournal_salesRole_returns403` | `POST /accounting/journals` + `X-User-Role: SALES` | 403 | `$.error.code` == `FORBIDDEN` |
| 7 | `createJournal_invalidLineSum_returns400` | `POST /accounting/journals` + 차변 100,000 / 대변 90,000 (합계 mismatch) | 400 | `$.error.code` == `INVALID_INPUT`, message contains `차/대 합계` |
| 8 | `postJournal_draft_transitionsToPosted` | (선행: createJournal) `POST /accounting/journals/{id}/post` + ACCOUNTANT | 200 | `$.data.status` == `POSTED`, `$.data.postedAt` != null, `$.data.postedBy` != null |
| 9 | `postJournal_posted_returns409` | (선행: post 완료) `POST /accounting/journals/{id}/post` 재호출 | 409 | `$.error.code` == `CONFLICT`, message contains `이미 확정` |
| 10 | `reverseJournal_posted_createsReverseEntry` | (선행: post 완료) `POST /accounting/journals/{id}/reverse` + ACCOUNTANT | 200 | `$.data.status` == `REVERSED`, 별도 GET `/accounting/journals?reverseOf={id}` → 1건, 신규 분개 차/대 swap 검증 |
| 11 | `reverseJournal_draft_returns409` | DRAFT 상태 분개에 reverse 호출 | 409 | `$.error.code` == `CONFLICT`, message contains `POSTED 상태만` |
| 12 | `getJournal_unknownId_returns404` | `GET /accounting/journals/{random-uuid}` | 404 | `$.error.code` == `NOT_FOUND` |

### 2.3 TrialBalanceControllerIT (2건) — 시산표 집계 + 권한

| # | 메서드명 | 입력 | 기대 status | jsonPath assertion |
|---|---|---|---|---|
| 13 | `findByPeriod_aggregates_groupsByCategory` | (선행: 분개 3건 POST) `GET /accounting/balances?period=202605` + ACCOUNTANT | 200 | `$.data.length()` == 7 (그룹 수, ASSET/LIABILITY/EQUITY/REVENUE/COGS/SGA/OTHER), 각 그룹별 `debitTotal`/`creditTotal` 합계 일치, `$.data[?(@.category=='ASSET')].debitTotal` 검증 |
| 14 | `findByPeriod_unauthRole_returns403` | `GET /accounting/balances?period=202605` + `X-User-Role: SALES` | 403 | `$.error.code` == `FORBIDDEN` |

---

## 3. 권한 매트릭스 7-tier

`feedback_role_naming_full.md` 준수 — 풀네임 사용. Designer ux-flow.md §4 6-role 표 + AUDITOR/INVENTORY 추가.

| Role | GET /accounts | POST /journals (생성) | POST /journals/{id}/post | POST /journals/{id}/reverse | GET /balances |
|---|---|---|---|---|---|
| MASTER | 200 | 201 | 200 | 200 | 200 |
| ACCOUNTANT | 200 | 201 | 200 | 200 | 200 |
| MANAGER | 200 (조회 OK) | **403** | **403** | **403** | **403** |
| SALES | 200 (조회 OK) | **403** | **403** | **403** | **403** |
| WAREHOUSE | 200 (조회 OK) | **403** | **403** | **403** | **403** |
| INVENTORY | 200 (조회 OK) | **403** | **403** | **403** | **403** |
| AUDITOR | 200 | **403** (작성 불가) | **403** | **403** | 200 (감사 read-only) |

> 결정 근거: Plan Q9 (분개 권한 = ACCOUNTANT/MASTER). AUDITOR 는 회계 감사 read-only — 시산표/계정과목 조회 OK, 분개 변경 권한 X. 계정과목 트리는 모든 인증 사용자가 참조 가능 (slip 작성 시 lookup 등 — Plan §4 `/accounting/accounts` ALL_AUTH).

### 3.1 IT 권한 케이스 매핑

| 시나리오 # | 검증 role | 검증 endpoint |
|---|---|---|
| 5 | MANAGER | POST /journals → 403 |
| 6 | SALES | POST /journals → 403 |
| 14 | SALES | GET /balances → 403 |

> BE 팀에 추가 위임: WAREHOUSE / INVENTORY / AUDITOR 권한 케이스는 단일 parameterized test (`@ParameterizedTest` + roles enum) 로 묶어도 무방.

---

## 4. BE Layer 4 시그니처 가정 표 (Plan §1.3 라이프사이클)

본 표는 BE 팀 도메인 메서드 시그니처에 대한 QA 가정 — PM 통합 단계에서 BE 팀과 의미 정렬 필수 (`feedback_pm_integration_build_check.md` Layer 4 회고).

| 도메인 메서드 | 시그니처 가정 | from status | to status | 부수효과 | IT 시나리오 # |
|---|---|---|---|---|---|
| `Journal.create(...)` | static factory, lines + journalDate + sourceType + memo | (신규) | DRAFT | journalNo 생성 (yyyyMMdd-N), 차/대 합계 라인 검증 (생성시는 warning, POST 시 enforce) | 4 |
| `Journal.post(UUID userId)` | instance 메서드, throws IllegalStateException if not DRAFT | DRAFT | POSTED | 차/대 합계 강제 검증, postedAt=now / postedBy=userId, AccountBalance refresh (period=yyyyMM) | 8 |
| `Journal.reverse(UUID userId)` | instance 메서드, returns 신규 Journal (역분개), throws if not POSTED | POSTED | REVERSED | 동일 일자에 차/대 swap 한 신규 Journal (status=POSTED) 자동 생성, 원분개 status=REVERSED, 신규 분개의 `reverseOfJournalId` = 원분개 id, 신규 분개 적요 prefix `REVERSE: {원journalNo}` | 10 |
| `JournalLine.requireBalanced(List<JournalLine>)` | static helper, throws BusinessException(`차/대 합계 불일치`) | — | — | sum(debit) == sum(credit) 검증, 라인 수 >= 2 검증 | 7 |
| `AccountBalance.refresh(...)` | service-level, period 단위 집계 | — | — | post() 시점 호출 — 단순 view 일 경우 skip 가능 (A2 결정) | 13 |
| `JournalNumberService.next(LocalDate)` | service 메서드, slip-service `SlipNumberService` 답습 | — | — | yyyyMMdd-N 시퀀스 (일자 별 1부터), DB UNIQUE constraint + retry on collision | 4 |

### 4.1 Layer 매핑 (`feedback_pm_integration_build_check.md`)

| Layer | 검증 대상 | QA 책임 |
|---|---|---|
| Layer 1 (compile) | 신규 entity / DTO / repository | BE 팀 Gradle assemble — QA 는 IT 실패 시 시그니처 mismatch 판단 |
| Layer 2 (Docker IT) | Testcontainers + Postgres | QA 시나리오 14건 검증 |
| Layer 4 (도메인 의미) | post / reverse 의미 정렬 | **본 §4 표 기반 BE-QA 사전 align 의무** |
| Layer 5 (계약) | API 응답 schema | jsonPath assertion 14건 + UUID 가드 §5 |

---

## 5. UUID 비공개 가드 (`feedback_uuid_no_user_visibility.md`)

본 슬라이스 endpoints 는 **관리자 (ACCOUNTANT/MASTER) 전용** 이므로 UUID 노출이 절대 금지는 아닙니다 (admin endpoint 예외). 단, **사용자 표시 우선순위는 비즈니스 식별자**:

| 응답 필드 | 비즈니스 식별자 (우선) | UUID (참고) | IT verify |
|---|---|---|---|
| Journal | `journalNo` (`20260504-3`) | `id` (UUID) | 시나리오 4: `$.data.journalNo` 형식 정규식 검증 + UI 표시 우선 |
| AccountBalance | `accountCode` (`805`), `accountName` (`임차료`) | (PK = code+period, no UUID) | 시나리오 13: `$.data[*].accountCode` 노출, UUID 부재 (애초에 없음) |
| ChartOfAccount | `code` (`805`), `name` | (PK = code, no UUID) | 시나리오 1: `$.data[*].code` |
| JournalLine | `accountCode`, `accountName`, `lineNo` | `id` (UUID) | 시나리오 4: line UUID 는 응답에 noisy — 가능하면 미노출 권장 (BE 팀 결정) |

### 5.1 UI / 사이드바 노출 가드

- `/accounting/journals/{id}` URL 의 id 는 UUID 이지만 **모든 화면 표시는 `journalNo`** (Designer wireframes.md `JournalDetailPage` 헤더)
- 사이드바 / 토스트 메시지 모두 `journalNo` (`"분개 20260504-3 확정 완료"`)

### 5.2 IT verify 항목 (BE 팀 추가 위임)

```
시나리오 4 추가 검증:
  jsonPath("$.data.journalNo").value(matchesPattern("^\\d{8}-\\d+$"))

시나리오 13 추가 검증:
  jsonPath("$.data[0].accountCode").value(matchesPattern("^\\d{3}$"))
  jsonPath("$.data[0].accountName").isNotEmpty()
```

---

## 6. 회귀 가드 — 기존 7 서비스 무영향

Plan §8 (회귀 위험 평가) 인용 — 본 슬라이스는 **신규 서비스 추가만** 하므로 기존 service 무수정.

| 서비스 | 변경 여부 | 회귀 위험 | QA 검증 |
|---|---|---|---|
| api-gateway | **무변경** (`/api/accounting/**` 라우트 기등록 — Plan §1) | None | gateway IT (있다면) green 유지 |
| auth-service | **무변경** | None | 기존 IT green |
| user-service | **무변경** | None | 기존 IT green |
| product-service | **무변경** | None | 기존 IT green |
| inventory-service | **무변경** | None | 기존 IT green |
| slip-service | **무변경** (A3 별도 슬라이스 — `complete()` 시점 RestClient 호출 추가는 deferred) | None | SlipLifecycleControllerIT green 유지 |
| logging-service | **무변경** | None | 기존 IT green |
| eureka-server | **무변경** | None | (운영 검증) |

### 6.1 PM 통합 사전 확인 (`feedback_pm_integration_build_check.md`)

PM 통합 단계에서:
1. `gradle assemble` 전체 모듈 — Layer 1 회귀 검증
2. `gradle :services:accounting-service:test` — accounting IT 14건 green
3. (Optional, Docker 가용 시) `gradle :services:slip-service:test` — slip 회귀 검증
4. Layer 4 의미 정렬 — BE 팀 PR diff 검토 시 본 §4 표와 메서드 시그니처 1:1 매칭

---

## 7. 한국 표준 계정 시드 검증 (~50+ rows)

`project_korean_accounting.md` 의무 — 한국 일반기업회계기준 표준 계정과목 코드 시드.

### 7.1 Flyway V1 시드 카테고리별 최소 검증 (시나리오 1 추가 jsonPath)

| 그룹 | 코드 범위 | 최소 시드 건수 | 필수 노출 계정 (시나리오 1 jsonPath 검증 대상) |
|---|---|---|---|
| 100 자산 (ASSET) | 101-163 | 8+ | 101 현금, 102 보통예금, 110 외상매출금, 120 미수금, 130 상품, 141 토지, 142 건물 |
| 200 부채 (LIABILITY) | 201-230 | 4+ | 201 외상매입금, 210 미지급금, 220 부가세예수금, 230 단기차입금 |
| 300 자본 (EQUITY) | 301-343 | 3+ | 301 자본금, 320 자본잉여금, 341 이익잉여금 |
| 400 매출 (REVENUE) | 401-405 | 2+ | 401 상품매출, 405 매출에누리 |
| 500 매출원가 (COGS) | 501-512 | 2+ | 501 상품매출원가, 510 제품매출원가 |
| 800 판관비 (SGA) | 801-822 | 4+ | 801 급여, 805 임차료, 814 통신비, 820 접대비 |
| 900 영업외/법인세 (OTHER) | 901-991 | 3+ | 901 이자수익, 951 이자비용, 991 법인세비용 |
| **합계** | | **26+** (Plan §3 ~50건 목표 — 세부 확장은 BE 팀 시드 작성 시 결정) | |

### 7.2 IT verify 강화 (시나리오 1 추가 assertion)

```
시나리오 1 (findAll_returnsKoreanStandardChart50Plus) — BE 팀 작성 시 본 표 기준 강화:

mockMvc.perform(get("/accounting/accounts")
        .header("X-User-Role", "ACCOUNTANT"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(26)))
    // 7-그룹 대표 계정 spot-check
    .andExpect(jsonPath("$.data[?(@.code=='101')].name").value(contains("현금")))
    .andExpect(jsonPath("$.data[?(@.code=='110')].name").value(contains("외상매출금")))
    .andExpect(jsonPath("$.data[?(@.code=='201')].name").value(contains("외상매입금")))
    .andExpect(jsonPath("$.data[?(@.code=='220')].name").value(contains("부가세예수금")))
    .andExpect(jsonPath("$.data[?(@.code=='301')].name").value(contains("자본금")))
    .andExpect(jsonPath("$.data[?(@.code=='401')].name").value(contains("상품매출")))
    .andExpect(jsonPath("$.data[?(@.code=='501')].name").value(contains("상품매출원가")))
    .andExpect(jsonPath("$.data[?(@.code=='805')].name").value(contains("임차료")))
    .andExpect(jsonPath("$.data[?(@.code=='901')].name").value(contains("이자수익")))
    .andExpect(jsonPath("$.data[?(@.code=='991')].name").value(contains("법인세비용")));
```

---

## 8. fixtures.http (수동 검증 시나리오 5+)

위치: `services/accounting-service/src/test/resources/fixtures.http`

| 시나리오 # | 제목 | endpoint | 기대 |
|---|---|---|---|
| 1 | 계정과목 트리 조회 | GET /accounting/accounts | 200 + 50+ rows |
| 2 | 분개 입력 (DRAFT) — ACCOUNTANT | POST /accounting/journals | 201 + status=DRAFT |
| 3 | 분개 확정 (POST) — ACCOUNTANT | POST /accounting/journals/{id}/post | 200 + status=POSTED |
| 4 | 역분개 (REVERSE) — ACCOUNTANT | POST /accounting/journals/{id}/reverse | 200 + status=REVERSED + 신규 분개 자동 생성 |
| 5 | 시산표 조회 (월별) | GET /accounting/balances?period=202605 | 200 + 7-그룹 합계 |
| 6 | 권한 가드 — SALES POST 시도 | POST /accounting/journals + SALES | 403 |
| 7 | 차/대 합계 mismatch | POST /accounting/journals (차 100,000 / 대 90,000) | 400 |
| 8 | 이미 POSTED 분개 재 POST | POST /accounting/journals/{id}/post (2회) | 409 |
| 9 | DRAFT 분개 reverse 시도 | POST /accounting/journals/{id}/reverse | 409 |

---

## 9. 회고 가드 적용 체크리스트

- [x] `feedback_pm_integration_build_check.md` — Layer 1+2+4+5 명시 (본 §1, §4, §6)
- [x] `feedback_multi_agent_team_pattern.md` — IT Java 자체 작성 X, BE 위임. QA 는 시나리오 표 + fixtures + qa-report 만 (본 §0)
- [x] `feedback_uuid_no_user_visibility.md` — admin endpoint 라 UUID 노출 OK 이지만 journalNo / accountCode 우선 (본 §5)
- [x] `feedback_role_naming_full.md` — Role 표기 풀네임 (MASTER/MANAGER/ACCOUNTANT/SALES/WAREHOUSE/INVENTORY/AUDITOR) (본 §3)
- [x] `feedback_korean_commits.md` — 본 보고서 한국어 작성
- [x] `feedback_testcontainers_windows_docker.md` — Docker skip 가드 (본 §1.1)
- [x] `feedback_it_mockbean_external_clients.md` — 외부 client 추가 시 즉시 @MockBean (본 §1.2)
- [x] `feedback_korean_path_jdk.md` — 한글 경로 trap 인지 (본 §1.3)
- [x] `feedback_function_documentation.md` — BE 팀 IT 작성 시 한국어 Javadoc 의무 (위임)

---

## 10. 다음 단계

1. **BE 팀** (parallel 진행 중) — 본 §2 시나리오 14건 IT 코드 작성, §4 시그니처 표 1:1 매칭
2. **PM 통합** — 5-team 결과물 수신 후 Layer 1+2+4+5 사전 검증
3. **PR #27 발행** — 본 qa-report 인용, fixtures.http 첨부
4. **A3 별도 슬라이스** (deferred) — slip `complete()` → 자동 분개 RestClient 추가 시 본 보고서 §1.2 외부 client @MockBean 의무 재적용
5. **A4 별도 슬라이스** (deferred) — Partner Service + AR/AP 권한 매트릭스 확장
