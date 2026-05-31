## Claude 5-agent 사이클 1 통합 리뷰 (head `8d6a1f0` 또는 PR HEAD)

> tech-manager 통합 — FE/Designer/QA/DevOps. SP-08-6-4 매출 인쇄 양식 P1.

### CI 상태

22+ SUCCESS / 잔여 IN_PROGRESS / FAILURE 0 + GitGuardian SUCCESS.

### 결함 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | Designer | **HIGH (지적 3)** | `mock-02-invoice-full.html` `@media print` 블록 | `.invoice-seal` / `.statement-seal` `print-color-adjust: exact` 미선언 — 흑백 프린터에서 [인] 인장 색상 소실. 양식 본 컴포넌트 (`SalesInvoicePrintPage.tsx` + `SalesTransactionStatementPrintPage.tsx`) 에도 동일 누락 가능 |
| 2 | FE | **Must Fix (M1)** | `SalesInvoicePrintPage.tsx` L1 JSDoc | 경로 표기 `/invoice-slip` → `/invoice` |
| 3 | FE | **Must Fix (M2)** | `routes/index.tsx` L17 주석 | 경로 표기 `/invoice-slip` → `/invoice` |
| 4 | Designer | MEDIUM (지적 1) | mock-01 합계 5셀 vs 실 컴포넌트 단일 셀 | TSX 합계 구조 spec §6 5셀 grid 적용 검증 |
| 5 | Designer | MEDIUM (지적 4) | 단가 VAT 안내 | 양식 본문에 "단가는 공급가액 기준(VAT 별도)" 한 줄 문구 추가 |
| 6 | FE | Should Fix (S1) | `nowPrintedAt`/`fmtDatetime` 3벌 중복 | `PrintLayout` 또는 `printUtils.ts` 추출 |
| 7 | FE | Should Fix (S2) | 부가세 round vs floor 혼용 | SP-08-5-5 동일 후속 (calcAmounts 통일) |
| 8 | Designer | LOW (지적 2) | mock-02 월/일 컬럼 분리 | spec §12 단일 `MM/DD` 컬럼 통일 |
| 9 | QA | 권고 | Playwright T2 candidate | `SalesInvoicePrintPage` candidate 추가 (T2 fallback 우회 → 직접 검증) |

### 각 agent 종합

| Agent | 판정 |
|---|---|
| FE | 사이클 2 필요 (Must Fix 2 + Should Fix 2) |
| Designer | **CHANGES REQUESTED** (HIGH 1 + MEDIUM 2 + LOW 1) |
| QA | **APPROVE** (5/5 PASS, 권고 1) |
| DevOps | **APPROVE** (CI clean, BE 영향 없음) |

### TM 결정 (사용자 6/7회차 + 리뷰 규칙 엄수)

**1c Claude fix 후보**:
1. Designer HIGH (지적 3): `SalesInvoicePrintPage` / `SalesTransactionStatementPrintPage` 컴포넌트 + mock HTML 의 `@media print { .invoice-seal, .statement-seal { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }` 추가
2. FE Must Fix M1/M2: JSDoc 경로 표기 `/invoice-slip` → `/invoice` (SalesInvoicePrintPage L1 + routes/index.tsx L17)
3. Designer MEDIUM 지적 1: 거래명세서 합계 5셀 grid (수량/공급가액/VAT/합계/인수) — SalesTransactionStatementPrintPage 검증 + 필요 시 정정
4. Designer MEDIUM 지적 4: 거래명세서 푸터 또는 비고에 "단가는 공급가액 기준 (VAT 별도)" 안내 문구 추가
5. FE Should Fix S1: `nowPrintedAt`/`fmtDatetime` 유틸 추출 (`print/printUtils.ts` 신규 + 3 컴포넌트 import 통일) — 또는 후속
6. FE Should Fix S2: 부가세 `Math.round` vs `Math.floor` 혼용 통일 — calcAmounts 일관 (SP-08-5-5 회고 동시 fix 검토)
7. Designer LOW 지적 2: 계산서 월/일 컬럼 단일 `MM/DD` 통일
8. QA 권고: Playwright T2 INVOICE_CANDIDATES 에 `SalesInvoicePrintPage.tsx` 추가

**CI green 도달 + 1c fix push 후 Codex 2a review 진행**.

**tech-manager — 2026-05-18**
