## Claude 5-agent 사이클 1 통합 리뷰 (head A `f8d9876` 또는 PR HEAD)

> tech-manager 통합. SP-08-6-5 일마감 + 원장. 사용자 6/7회차 정책.

### CI 상태

**20/20 SUCCESS** (accounting+partner 그룹 포함).

### 결함 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | BE | **CRITICAL** | `DailyClosing.java:117` | `this.version = 0L;` 수동 초기화 — JPA @Version 영속화 위임 — OptimisticLockException 위험. **제거 필수** |
| 2 | BE | **MAJOR** | `DailyClosingRepository.findByDateRange` | JPQL countQuery 명시 누락 — Spring Data JPA count 자동 파생 실패 위험. `@Query(value=..., countQuery=...)` 추가 |
| 3 | BE | **MAJOR** | `DailyClosingController.unlock` | POST `/unlock` REST 위반 — PATCH `/{closingDate}/lock` (body locked:false) 또는 DELETE 형태 + closingDate path variable |
| 4 | BE | **MAJOR** | V15 partial unique + IT | soft-delete 후 재마감 시나리오 IT 누락. testReopenAfterSoftDelete 추가 |
| 5 | FE | **MAJOR (결함 1)** | `GeneralLedgerPage.tsx` 라인 테이블 | 날 `<table>` 직접 — design-system `DataTable` 컴포넌트 미사용. DataTable<GeneralLedgerLine> + columns 정의로 교체 |
| 6 | BE | CRITICAL 1 (informational) | `DailyClosingService.close()` 108-121 | 신규 생성 시 create() + recalculate() 중복 호출 (동작 영향 없음 — 정리 권고) |
| 7 | BE | MINOR | `LedgerService.getLedger()` 89-108 | N+1 partnerLookupClient HTTP 호출 — partnerId Set 일괄 lookup 으로 변경 |
| 8 | BE | MINOR | DailyClosingIT | unlock 시나리오 IT 누락 (MASTER 성공 + 타 role 403) |
| 9 | BE | MINOR | DailyClosingController.unlock | `@ResponseStatus(HttpStatus.OK)` 누락 (OpenAPI 일관) |
| 10 | FE | 결함 2 | `DailyClosingPage.tsx` + `GeneralLedgerPage.tsx` | `today()` 3곳 중복 (MonthEndClosingPage 포함). 공용 util 추출 |
| 11 | FE | 결함 3 | `accounting.ts canAccessGeneralLedger` | 사용처 없는 dead code — 사용하거나 제거 |
| 12 | FE | 결함 4 | `routes/index.tsx` JSDoc | 신규 라우트 2건 미반영 |
| 13 | FE | 결함 5 | DailyClosingPage vs GeneralLedgerPage `fmtKrw` | 음수 처리 불일치 (raw vs `△`). 회계 표준 `△` 통일 |
| 14 | Designer | 조건부 (지적 1) | `PartnerLedgerView.module.css` L19 | 인쇄 폰트 Malgun Gothic (고딕) → Batang/바탕 (명조 계열) 전환 |
| 15 | QA | open item | gen_pngs.py OUT 경로 | Linux-style `/c/dev/...` → 상대경로 |

### 각 agent 종합

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (CRITICAL 2 + MAJOR 3 + MINOR 3) |
| FE | 사이클 2 필요 (결함 1 MAJOR + 결함 2/3/4/5) |
| Designer | **조건부 승인** (인쇄 폰트 명조 전환) |
| QA | **APPROVE 0결함** |
| DevOps | **APPROVE** (CI 20/20 + Flyway V15 정합) |

### TM 결정 (사용자 6/7회차 정책 + 리뷰 규칙 엄수)

**1c Claude fix 후보 (CRITICAL/MAJOR 우선)**:
1. BE CRITICAL: `DailyClosing` `this.version = 0L;` 제거 (JPA 위임)
2. BE MAJOR: `DailyClosingRepository.findByDateRange` `@Query countQuery=...` 명시
3. BE MAJOR: `DailyClosingController.unlock` REST 설계 정정 (PATCH `/{closingDate}/lock` body `locked:false`) + closingDate path variable
4. BE MAJOR: `DailyClosingIT` testReopenAfterSoftDelete + testUnlockSuccess (MASTER) + testUnlockForbidden (ACCOUNTANT) 신규
5. FE MAJOR (결함 1): `GeneralLedgerPage` DataTable 교체
6. FE 결함 2: `clients/desktop/src/renderer/utils/dateUtils.ts` 신규 + today/sevenDaysAgo export + 3 페이지 import
7. FE 결함 3: `canAccessGeneralLedger` 사용 또는 제거
8. FE 결함 4: routes/index.tsx JSDoc 라우트 2건 추가
9. FE 결함 5: `fmtKrw` 공용 util + 음수 `△` 통일
10. Designer: `PartnerLedgerView.module.css` font-family Batang/바탕/HY신명조 명조 계열
11. BE MINOR: LedgerService partnerId Set 일괄 lookup
12. BE MINOR: unlock `@ResponseStatus(HttpStatus.OK)` + OpenAPI 일관
13. QA open: gen_pngs.py 경로 상대화

**CI green 도달 + 1c fix push 후 Codex 2a review 진행**.

**tech-manager — 2026-05-18**
