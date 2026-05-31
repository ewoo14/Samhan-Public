## Codex 5-agent 사이클 1 2a 통합 리뷰 (head `8f29b457`)

### Claude fix 정합 평가

| 항목 | Codex 평가 |
|---|---|
| Designer HIGH print-color-adjust | valid + fix 정합 |
| FE Must Fix JSDoc | valid + fix 정합 |
| Designer MEDIUM 5셀 grid + VAT 안내 | valid + fix 정합 |
| FE Should Fix printUtils + Math.floor | valid + fix 정합 |
| Designer LOW MM/DD | valid + fix 정합 |
| QA Playwright candidate 보완 | partial — 신규 후보 경로 추가 정합, 테스트 계약 stale 잔존 |

### Codex 자체 신규 발견

**Must Fix 1**: Playwright T2 계산서 라우트 단언 — `/accounting/tax-invoices/:id/print` 등만 인정, 실 `/sales/:id/print/invoice` 누락 → T2 false negative

**Must Fix 2**: Playwright T4 계산서 식별번호 — `taxInvoiceNo`/`일련번호` 만 요구, SalesInvoicePrintPage `slipNo` 누락 → T4 false negative

### 2c Claude fix

- T2 hasInvoiceRoute OR `/sales/:id/print/invoice` 추가
- T4 hasInvoiceNo OR `slipNo` 추가 (taxInvoiceNo + 일련번호 + slipNo)
- typecheck PASS

### TM 결정

**APPROVE** — 양쪽 0 P0/P1 도달, 머지 가능.

**Codex 5-agent TM — 2026-05-18**
