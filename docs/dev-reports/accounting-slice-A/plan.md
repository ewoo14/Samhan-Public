# Phase 4 Accounting Service Plan (Slice A — A1+A2 통합 MVP)

> **작성**: 2026-05-05 PM Claude (Plan agent 산출).
> **상태**: Open Question 9건 사용자 확정 대기 → Designer + 5-team 디스패치 예정.
> **PR 후보**: PR #27.

본 슬라이스는 **accounting-service (포트 8087, accounting_db) 신규 + 한국 일반기업회계기준 표준 계정과목 시드 + 분개장(Journal) 도메인 + 시산표** MVP 구축.

---

## 1. 서비스 개요
- 신규 모듈: `services/accounting-service` (port 8087, DB `accounting_db`)
- API Gateway 라우트 `/api/accounting/**` 는 이미 `application.yml` 에 등록 완료 — DevOps 무영향
- BaseEntity 7 audit + Soft delete + Flyway + ddl-auto=validate + RestClient + X-Internal-Token 답습

## 2. 도메인 모델

| Entity | 핵심 필드 | 비고 |
|---|---|---|
| ChartOfAccount | code VARCHAR(6) PK, name VARCHAR(100), category enum, parentCode, isLeaf, displayOrder | Flyway V1 시드 ~50건 |
| Journal | id UUID, journalNo VARCHAR(20) UNIQUE (yyyyMMdd-N), journalDate, sourceType enum, sourceRefId, status enum, postedAt/By, @Version | BaseEntity, @SQLRestriction |
| JournalLine | id, journal_id FK, lineNo, accountCode FK, debitAmount NUMERIC(15,2), creditAmount NUMERIC(15,2), partnerId nullable, memo | 차/대 합계 일치 enforce |
| AccountBalance | accountCode + period(yyyyMM) PK, debitTotal, creditTotal, balance | 시산표 view (배치 또는 mat view, A2 결정) |

### 라이프사이클 표 (Layer 4 의무)
| 메서드 | from status | to status | 부수효과 |
|---|---|---|---|
| `Journal.post()` | DRAFT | POSTED | 라인 합계 검증, postedAt/By 기입, AccountBalance refresh |
| `Journal.reverse()` | POSTED | REVERSED | 동일 일자에 차/대 swap 한 역분개 자동 생성, 원분개 status REVERSED 마킹 |

DRAFT 만 직접 수정 허용. POSTED 이후는 reverse 만 (audit safe — Q7 권장).

## 3. 한국 표준 계정과목 시드 (Flyway V1)

| 그룹 | 코드 범위 | 핵심 계정 |
|---|---|---|
| 100 자산 | 101-163 | 101 현금, 102 보통예금, 110 외상매출금, 120 미수금, 130 상품, 141 토지, 142 건물 |
| 200 부채 | 201-226 | 201 외상매입금, 210 미지급금, 220 부가세예수금, 230 단기차입금 |
| 300 자본 | 301-343 | 301 자본금, 320 자본잉여금, 341 이익잉여금 |
| 400 매출 | 401-405 | 401 상품매출, 405 매출에누리 |
| 500 매출원가 | 501-512 | 501 상품매출원가, 510 제품매출원가 |
| 800 판관비 | 801-822 | 801 급여, 805 임차료, 814 통신비, 820 접대비 |
| 900 영업외/법인세 | 901-991 | 901 이자수익, 951 이자비용, 991 법인세비용 |

(메모리 `project_korean_accounting.md` 의무 — 50+ rows seed)

## 4. API 스펙 (외부 9 + 내부 1)

| 메서드 | Path | 권한 | 설명 |
|---|---|---|---|
| GET    | `/accounting/accounts` | ALL_AUTH | 계정과목 트리 |
| POST   | `/accounting/journals` | ACCOUNTANT/MASTER | 수동 분개 입력 (DRAFT) |
| GET    | `/accounting/journals?from=&to=&status=` | ACCOUNTANT/MASTER | 페이지 조회 |
| GET    | `/accounting/journals/{id}` | ACCOUNTANT/MASTER | 단건 |
| POST   | `/accounting/journals/{id}/post` | ACCOUNTANT/MASTER | DRAFT → POSTED |
| POST   | `/accounting/journals/{id}/reverse` | ACCOUNTANT/MASTER | POSTED → REVERSED |
| GET    | `/accounting/balances?period=yyyyMM` | ACCOUNTANT/MASTER | 시산표 |
| GET    | `/accounting/receivables?partnerId=` | ACCOUNTANT/MASTER | (A4 deferred) 외상매출 |
| GET    | `/accounting/payables?partnerId=` | ACCOUNTANT/MASTER | (A4 deferred) 외상매입 |
| POST   | `/internal/journals/from-slip` | X-Internal-Token | (A3 deferred) slip-service 자동 분개 |

## 5. slip 연계 (A3 별도 슬라이스)
- **Option A (REST 동기) 추천**: auth/product 답습 패턴 일관성, RabbitMQ infra 존재하나 본 슬라이스에선 도입 부담
- 시점: **slip `complete()` (PROCESSING→INSPECTING)** — 출고 확정 시점에 매출/원가 계상

## 6. 단계별 sub-슬라이스
- **A1+A2 통합** (MVP, ~6일): 골격 + 시드 + Journal 도메인 + 수동 분개 + post/reverse + 시산표
- **A3** (별도): slip-accounting 자동 분개 (slip-service 회귀 위험)
- **A4** (별도): 외상매출/매입 + Partner 연계

## 7. Open Question — **사용자 확정 (2026-05-05)**

| Q | 결정 |
|---|---|
| Q1 슬립→분개 자동화 시점 | **A3 별도 슬라이스 deferred** (본 A1+A2 는 수동 분개만) |
| Q2 slip-accounting 통합 | **REST 동기** (auth/product 패턴 답습) |
| Q3 AR/AP 만기 관리 | **A4 별도 슬라이스 deferred** (본 슬라이스는 partnerId 필드만) |
| Q4 결산 자동화 | **Phase 5+ deferred** (본 슬라이스는 시산표까지) |
| Q5 VAT 부가세 신고서 | **Phase 5+ deferred** (부가세예수금/대급금 계정만 시드) |
| Q6 다중 통화 | **KRW only** (Phase 6+ 확장) |
| Q7 분개 무효화 | **reverse 분개 audit safe** (DRAFT 만 직접 수정) |
| Q8 Partner Service | **본 슬라이스: partnerId 컬럼만** + Partner 별도 슬라이스 |
| Q9 분개 권한 | **ACCOUNTANT/MASTER** (MANAGER 제외) |

## 8. 회귀 위험 평가
- A1+A2: 신규 서비스, 기존 7 무영향 (Gateway 라우트 기등록)
- A3 진입 시: slip-service `inspect/complete` 에 RestClient 호출 추가 — Layer 1+2+3+4+5 PM 통합 사전 검증 의무

## 9. 다음 단계
1. Open Questions Q1~Q9 사용자 확정
2. Designer agent (회계 대시보드 + 분개 입력 폼 wireframe + spec)
3. 5-team parallel 디스패치 (BE/FE/QA/DevOps + Designer 산출물 인용)
4. PM 통합 → PR #27 발행
