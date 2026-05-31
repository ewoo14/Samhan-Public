## Claude 5-agent 사이클 1 통합 리뷰 (head A `3f3f2c84`)

> SP-08-6-6 세금계산서 발행 회귀 가드 (옵션 A) — BE/FE/Designer/QA/DevOps 통합.

### 결정

**옵션 A 채택** — 기존 `POST /accounting/tax-invoices/{id}/issue` endpoint + FE TaxInvoiceDetailPage + 도메인 메서드 + IT 8 case 완비. 신규 emit endpoint 추가 시 API 중복.

### 결함 종합

| Agent | 판정 |
|---|---|
| BE | APPROVE (issueAlreadyIssued_409 IT 1 case 추가) |
| FE | APPROVE (변경 없음 — 기존 인프라 완비) |
| Designer | APPROVE (기존 디자인 재사용) |
| QA | APPROVE (Playwright 5 + PNG 4 + dev-report) |
| DevOps | APPROVE (Flyway V16 생략 권고, e_tax_external_id 재활용) |

### 부수 발견 (후속 슬라이스)

- TaxInvoiceListPage "일괄 발행" → `/accounting/hometax-export` vs `tax-invoice-batch.spec` TC-TIB-7 `/accounting/tax-invoices/batch` 경로 불일치
- NTS 실연동 (e-tax) 본격 구현 SP-09/10 후속

### TM 결정

**APPROVE** — 사이클 2 불필요, PM 자동 머지 가능.

**tech-manager — 2026-05-18**
