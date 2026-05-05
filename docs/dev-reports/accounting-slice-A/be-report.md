# Accounting Slice A — BE Report

> **작성**: 2026-05-04 BE agent.
> **상태**: 신규 accounting-service 골격 + Journal 도메인 + 한국 표준 계정과목 시드 + 시산표 MVP 완료.
> **PR 후보**: PR #27 일부 (BE).

본 문서는 Phase 4 Slice A (A1+A2 통합 MVP) BE 산출 요약. Designer/FE/QA/DevOps 산출물은 별도.

---

## 1. 신규 모듈 구조

```
services/accounting-service/
├── build.gradle                    (slip-service 답습, RabbitMQ 미포함)
└── src
    ├── main
    │   ├── java/com/samhanair/logis/accounting
    │   │   ├── AccountingServiceApplication.java
    │   │   ├── config
    │   │   │   ├── HeaderAuthenticationFilter.java   (X-User-* 헤더 가드)
    │   │   │   ├── InternalAuthProperties.java
    │   │   │   ├── InternalTokenGuard.java
    │   │   │   └── SecurityConfig.java               (모든 /accounting/** 인증 필수)
    │   │   ├── domain
    │   │   │   ├── AccountCategory.java              (8 enum + Korean 라벨)
    │   │   │   ├── ChartOfAccount.java               (BaseEntity, code PK VARCHAR(6))
    │   │   │   ├── Journal.java                      (BaseEntity + @Version + 라이프사이클)
    │   │   │   ├── JournalLine.java                  (debit/credit XOR 도메인 가드)
    │   │   │   ├── JournalNumberSequence.java        (yyyyMMdd-N 채번)
    │   │   │   ├── JournalSourceType.java            (SLIP/MANUAL/CLOSING)
    │   │   │   └── JournalStatus.java                (DRAFT/POSTED/REVERSED)
    │   │   ├── repository
    │   │   │   ├── ChartOfAccountRepository.java
    │   │   │   ├── JournalLineRepository.java        (시산표 집계 native projection)
    │   │   │   ├── JournalNumberSequenceRepository.java
    │   │   │   └── JournalRepository.java
    │   │   ├── service
    │   │   │   ├── AccountService.java               (트리 + leaf 검증)
    │   │   │   ├── JournalNumberService.java         (yyyyMMdd-N 포맷)
    │   │   │   ├── JournalService.java               (create/post/reverse/list/getOne)
    │   │   │   └── TrialBalanceService.java          (POSTED 라인 집계, 부호 규약)
    │   │   └── web
    │   │       ├── AccountController.java            (GET /accounting/accounts)
    │   │       ├── GlobalExceptionHandler.java       (BusinessException → ApiResponse)
    │   │       ├── JournalController.java            (5 endpoint)
    │   │       ├── TrialBalanceController.java       (GET /accounting/balances)
    │   │       └── dto/                              (8 DTO record)
    │   └── resources
    │       ├── application.yml                       (port 8087, profile local 포함)
    │       └── db/migration
    │           └── V1__init_accounting_service.sql   (스키마 + 한국 표준 계정 65행 시드)
    └── test
        └── java/com/samhanair/logis/accounting
            ├── domain/JournalDomainTest.java         (9 시나리오)
            ├── service/JournalServiceTest.java       (Mockito, 4 시나리오)
            └── it/
                ├── AbstractPostgresIT.java           (싱글턴 컨테이너 + Docker skip)
                ├── ChartOfAccountSeedIT.java         (3 시나리오)
                ├── JournalControllerIT.java          (7 권한/라이프사이클 시나리오)
                └── TrialBalanceControllerIT.java     (4 시나리오)
```

## 2. Journal 라이프사이클 (Layer 4 의무)

| 메서드 | from → to | 부수효과 | 회로 |
|---|---|---|---|
| `Journal.create` | (없음) → DRAFT | journalNo + journalDate + sourceType (MANUAL/SLIP/CLOSING) 기입, lines 빈 리스트 | service 가 `JournalNumberService.next` 채번 후 호출 |
| `Journal.addLine` | DRAFT → DRAFT | lines 에 JournalLine 추가 | DRAFT 가 아니면 CONFLICT |
| `Journal.removeLine` | DRAFT → DRAFT | lines orphan removal | DRAFT 가 아니면 CONFLICT |
| `Journal.post(actorUserId)` | DRAFT → POSTED | (1) 라인 1건 이상 검증, (2) 차변 합계 = 대변 합계 검증, (3) postedAt/By 기입 | mismatch / 라인 0건 / DRAFT 아님 → CONFLICT |
| `Journal.markReversed` | POSTED → REVERSED | status 만 변경 (신규 역분개 Journal 생성은 service 책임) | POSTED 아니면 CONFLICT |
| `Journal.linkReversal(uuid)` | (REVERSED 분개) | reversedJournalId 기입 | service 가 신규 역분개 저장 후 호출 |

**JournalService.reverse(id, actorUserId)** — 단일 트랜잭션 처리:
1. 원분개 fetch
2. 차/대 swap 한 신규 Journal 생성 (description="[역분개] {원}", sourceRefId=원분개 ID)
3. 신규 라인 추가 (debit/credit swap)
4. 신규 Journal `post(actorUserId)` (자체 합계 검증 + POSTED)
5. 원분개 `markReversed` + `linkReversal(신규 ID)`
6. 응답: 신규 역분개 단건 (FE 가 reversedJournalId 로 원분개 추적)

## 3. 권한 매트릭스 (Plan §4 + Q9)

| 메서드 | Path | 권한 | 응답 코드 |
|---|---|---|---|
| GET    | `/accounting/accounts` | ALL_AUTH | 200 |
| POST   | `/accounting/journals` | ACCOUNTANT, MASTER | 201 (DRAFT) |
| GET    | `/accounting/journals?from=&to=&status=&page=` | ACCOUNTANT, MASTER | 200 (Page) |
| GET    | `/accounting/journals/{id}` | ACCOUNTANT, MASTER | 200 (라인 포함) |
| POST   | `/accounting/journals/{id}/post` | ACCOUNTANT, MASTER | 200 → POSTED |
| POST   | `/accounting/journals/{id}/reverse` | ACCOUNTANT, MASTER | 200 → 신규 역분개 POSTED |
| GET    | `/accounting/balances?period=yyyyMM` | ACCOUNTANT, MASTER | 200 |

**Q9 결정**: ACCOUNTANT/MASTER 만 (MANAGER 제외). MANAGER 는 사후 결재/승인 절차 별도 슬라이스에서 다룸.

## 4. 한국 표준 계정과목 시드 (Plan §3)

V1__init_accounting_service.sql 에 **65 행** 시드 (Plan 요구 50+ 충족):

| 그룹 | 코드 범위 | 핵심 leaf | 행 수 |
|---|---|---|---|
| 100 자산 (ASSET) | 100-163 | 101 현금, 102 보통예금, 110 외상매출금, 130 상품, 142 건물 | 22 |
| 200 부채 (LIABILITY) | 200-260 | 201 외상매입금, 210 미지급금, 220 부가세예수금, 230 단기차입금 | 10 |
| 300 자본 (EQUITY) | 300-343 | 301 자본금, 320 자본잉여금, 341 이익잉여금 | 6 |
| 400 매출 (REVENUE) | 400-405 | 401 상품매출, 404 제품매출, 405 매출에누리 | 4 |
| 500 매출원가 (COST_OF_SALES) | 500-512 | 501 상품매출원가, 510 제품매출원가, 512 재료비 | 4 |
| 800 판관비 (SGA) | 800-833 | 801 급여, 805 잡급, 814 통신비, 820 수선비 | 19 |
| 900 영업외 (NON_OPERATING) | 900-970 | 901 이자수익, 951 이자비용, 970 잡손실 | 7 |
| 990 법인세 (INCOME_TAX) | 991 | 991 법인세비용 | 1 |
| 합계 |  |  | **65** |

각 root 코드(100/200/300/400/500/800/900) 는 `is_leaf=FALSE` 통제 계정 — 분개 라인 사용 시 service 레이어가 INVALID_INPUT (400) 반환.

## 5. 잔액 부호 규약 (TrialBalanceService)

| 카테고리 | 잔액 식 | 비고 |
|---|---|---|
| ASSET, COST_OF_SALES, SGA, INCOME_TAX | debit - credit | 차변 잔액 양수 |
| LIABILITY, EQUITY, REVENUE, NON_OPERATING | credit - debit | 대변 잔액 양수 |

## 6. 회귀 가드 준수 (PR #21~#26 회고)

| 가드 | 본 슬라이스 영향 | 조치 |
|---|---|---|
| Layer 4 라이프사이클 표 | 적용 | §2 표 commit message + 본 문서 명시 |
| @Column unique=true partial INDEX mismatch | 회피 | JournalNumberSequence.journalDate 는 V1 의 full UNIQUE constraint (`ux_journal_number_sequences_date`) 사용 — SlipNumberSequence 답습 |
| @Lob byte[] BYTEA mismatch | 미적용 (Slice A 서명/바이너리 0) | n/a |
| VARCHAR(N) 컨벤션 | 적용 | V1 모든 문자열 VARCHAR (CHAR 0건) |
| @MockBean 외부 client | 미적용 (외부 client 0 — A3 진입 시 ProductClient/SlipClient mock) | n/a |
| UUID 미노출 | 부분 적용 | 화면 표시는 journalNo / accountCode / journalDate / 금액. id 는 mutation path 용 (FE 에서 숨김 권장) |
| 한국어 Javadoc + springdoc @Operation | 적용 | 모든 도메인/service/controller |
| 한국어 commit | 적용 | 다음 commit |

## 7. 검증 결과

| 검증 | 결과 |
|---|---|
| `./gradlew :services:accounting-service:compileJava` | PASS |
| `./gradlew :services:accounting-service:compileTestJava` | PASS |
| `./gradlew :services:accounting-service:test` (unit) | **13 PASS** (JournalDomainTest 9 + JournalServiceTest 4) |
| `./gradlew :services:accounting-service:test` (IT) | **14 SKIP** (Docker npipe — Windows 한계, CI Linux 활성 예정) |
| `./gradlew :services:api-gateway:assemble` | PASS |
| `./gradlew :services:accounting-service:assemble` | PASS |

**참고**: IT 14건은 메모리 `feedback_testcontainers_windows_docker.md` 의 Windows + Docker Desktop npipe 한계로 로컬 skip. CI Linux 환경 또는 `DOCKER_HOST=tcp://localhost:2375` 노출 시 활성. AbstractPostgresIT.DockerAvailableCondition 이 fail 대신 skip 처리.

## 8. 신규 endpoint 표 (FE 연동용)

```
GET    /api/accounting/accounts                       → AccountTreeNodeResponse[]
POST   /api/accounting/journals      (CreateJournalRequest) → JournalDetailResponse
GET    /api/accounting/journals?from=&to=&status=&page=&size= → Page<JournalResponse>
GET    /api/accounting/journals/{id}                  → JournalDetailResponse
POST   /api/accounting/journals/{id}/post             → JournalDetailResponse
POST   /api/accounting/journals/{id}/reverse          → JournalDetailResponse (신규 역분개)
GET    /api/accounting/balances?period=yyyyMM         → TrialBalanceResponse
```

API Gateway 라우팅은 이미 `application.yml` 에 `/api/accounting/**` 등록 완료 (Plan §1 — DevOps 무영향).

## 9. 회귀 위험

- **신규 서비스** — 기존 7 서비스 무영향. settings.gradle / build.gradle leafProjects 에 1줄 추가만.
- **DB**: 신규 `accounting_db` 필요 (DevOps 가 PostgreSQL initdb 추가).
- **A3 진입 시**: slip-service 의 `complete()` 에 RestClient 호출 추가 → Layer 1+2+3+4+5 PM 통합 사전 검증 의무 (메모리 `feedback_pm_integration_build_check.md`).

## 10. 다음 단계

1. FE 가 본 endpoint 7건 + Designer wireframe 으로 화면 구현
2. QA 가 라이프사이클 + 권한 매트릭스 + 시산표 시나리오 작성
3. DevOps 가 `accounting_db` initdb + Eureka 등록 검증
4. Designer 의 `01_account_tree.html` ~ `05_trial_balance.html` 5 mock 을 React 컴포넌트로 매핑
