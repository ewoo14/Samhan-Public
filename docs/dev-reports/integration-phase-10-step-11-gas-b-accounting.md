# Phase 10 step-11 (PR-E2) — GAS B accounting 4건 이식 — 원장/거래명세서/계산서/일마감

> 본 dev-report 는 PR (`feature/integrated-phase-10-step-11-gas-b-accounting`) 의 종합 작업 보고. PR #117 (W10-step-10 = PR-E1, GAS B 11건 중 7건 — slip/arologis/inventory/notification 통합) 머지 후, 사용자 명시 GAS B accounting 카테고리 잔여 4건 (원장 / 거래명세서 / 계산서 / 일마감) 을 accounting-service native 이식하여 GAS B 11건 매핑 100% 완성.

## 1. 배경

Samhan Public 운영 GAS 11 도구 (사용자 분류 B) 중 회계 도메인 4건은 PR-E1 (slip/arologis/inventory/notification) scope 외로 accounting-service 별도 슬라이스 PR-E2 로 위임 (D-P10-19 후속 일관). 본 PR-E2 시점:

- **자체 분개 + 세금계산서 자동 조회** — accounting-service `journal_entries / journal_lines / tax_invoices` 테이블 기반 (이카운트 의존 0)
- **한국 일반기업회계기준 표준 코드 활용** — 401 (매출) / 110 (외상매출금) / 255 (부가세예수금) 코드 기반 집계 (memory `project_korean_accounting`)
- **외부 client 3종 도입** — `ProductClient` (product-service `/internal/products/by-id`), `PartnerLookupClient` (partner-service `/internal/partners/{partnerCode}`), `ChatRoomMappingClient` (notification-service `/internal/chat-rooms/by-partner-code`) — Feign + IT @MockBean 격리 (memory `feedback_it_mockbean_external_clients`)
- **POI 5.2.5 도입** — 홈택스 일괄 양식 xlsx 100건 sheet 분할 (Apache License 2.0)

본 PR-E2 머지 후 GAS B 11건 native 이식 100% 완성, 잔여 GAS C/D 6건 (PR-F) 진입 가능.

## 2. 산출물

### 2-1. Phase A — Backend 1 통합 (5 task) + Designer 2 view (2 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `69fd8f0` | Designer | `clients/desktop/src/renderer/print/PartnerLedgerView.tsx` (+CSS Module) — 거래처 snapshot + 단톡방 매핑 + 분개 line + 누적 잔액 + @media print A4. `clients/desktop/src/renderer/print/StatementBatchView.tsx` (+CSS Module) — 거래처별 page-break-after batch + 라인 snapshot + Malgun Gothic. 1차 mock (사용자 Edge 캡처 후 2~5차 iteration 예정 — `feedback_print_design_iteration`) |
| `c48e156` | BE-1 (accounting-service) 통합 5 task | `AccountingReportController` 5 endpoint 통합 (BE-A8 매출 집계 / BE-A9 원장 / BE-A10 거래명세서 batch / BE-A11 홈택스 export / BE-A12 일별 마감 detail) — 모두 `ACCOUNTANT/MASTER` 가드. `SalesAggregateService` (401/110 코드 합계) + `LedgerImageService` (분개 라인 + 누적 잔액) + `StatementBatchService` (거래처별 그룹핑) + `HometaxExportService` (POI 100건 sheet 분할) + `MonthEndCloseService.getDailyDetail` (read-only 일별 detail). 외부 client 3종 (`ProductClient` / `PartnerLookupClient` / `ChatRoomMappingClient`) Feign + 기본 fail-soft. JournalLineRepository / TaxInvoiceRepository 신규. POI 5.2.5 의존성 추가 (build.gradle). 단위 테스트 20 case 신규 (Sales 4 / Ledger 4 / Statement 3 / Hometax 5 / DailyClosing 4) — 전부 PASS, failures/errors 0 |

### 2-2. Phase B — Desktop FE 4 (3 commits, FE-8 + FE-9 통합 흡수 + 복구)

| Commit | Role | 산출 |
| --- | --- | --- |
| `b3dfb4b` | FE-7 | `api/partnerLedgerApi.ts` (집계 + 원장 detail) + `routes/PartnerLedgerPage.tsx` (`/accounting/partner-ledger`, 일괄 인쇄 + Designer PartnerLedgerView 통합) + `/print/partner-ledger` route + 사이드바 entry "거래처 원장" (회계 그룹) |
| `154f46e` | FE-10 | `routes/MonthEndClosingPage.tsx` 일별 detail 표 보강 (productName / discount / supply / vat / total) + `closingApi.getDailyClosingDetail` + 일별 CSV 다운로드 |
| `55ebad5` | FE-8 + FE-9 통합 (working tree 복구) | **multi-agent collision 복구** — FE-10 작업의 `git reset --soft` 가 FE-8 (commit `eb473b4`) + FE-9 (commits `6cf9646` / `8f62b57`) 를 destroy 하여 working tree 에 unstaged 로 남은 산출을 복구 commit 으로 일괄 정리. `api/statementBatchApi.ts` + `routes/StatementBatchPage.tsx` (거래명세서 일괄 + page-break per partner) + `api/hometaxExportApi.ts` + `routes/HometaxExportPage.tsx` (홈택스 일괄 binary xlsx + 한국어 파일명) + `AppLayout` 회계 그룹 entry 2건 ("거래명세서 일괄" / "홈택스 일괄 양식") + `/accounting/statement-batch` + `/accounting/hometax-export` + `/print/statement-batch` route 등록 |

### 2-3. GAS B 11건 매핑 — PR-E1 (7건) + PR-E2 (4건) = 100% 완성

| GAS 도구 | PR | 산출 |
| --- | --- | --- |
| 1. DPS비교 | PR-E1 (#117) | inventory `POST /warehouse/audit/dps-compare` + desktop `/warehouse/dps-compare` |
| 2. 가배차 | PR-E1 (#117) | arologis `GET /admin/arologis/dispatches/pre-classify` + desktop `/arologis/pre-classify` |
| 3. 미배차 | PR-E1 (#117) | arologis `GET /admin/arologis/dispatches/unassigned` + desktop `/arologis/unassigned` |
| 4. 지방가배차 | PR-E1 (#117) | arologis `GET /admin/arologis/dispatches/regional` + desktop `/arologis/pre-classify` 토글 |
| 5. 내일자전표 이미지 | PR-E1 (#117) | slip `GET /slips/next-day-image-data` + desktop `/sales/next-day-slip` + Designer NextDaySlipView |
| 6. 전표정리 | PR-E1 (#117) | slip `GET /slips/cleanup` + desktop `/sales/slip-cleanup` |
| 7. 배차안내 SMS | PR-E1 (#117) | notification `POST /admin/notifications/dispatch-batch/{preview,send}` + desktop `/dispatch/sms` |
| 8. 원장 | **PR-E2 (본 PR)** | accounting `GET /accounting/journals/ledger-data` + desktop `/accounting/partner-ledger` + Designer PartnerLedgerView |
| 9. 거래명세서 | **PR-E2 (본 PR)** | accounting `GET /accounting/statements/batch-data` + desktop `/accounting/statement-batch` + Designer StatementBatchView |
| 10. 계산서 (홈택스) | **PR-E2 (본 PR)** | accounting `GET /accounting/tax-invoice/hometax-export` (xlsx) + desktop `/accounting/hometax-export` |
| 11. 일마감 | **PR-E2 (본 PR)** | accounting `GET /accounting/closings/daily` + desktop `/accounting/month-end-closing` (FE-10 detail 보강) |

## 3. 검증

### 3-1. 풀빌드
- `./gradlew assemble -x test` → BUILD SUCCESSFUL

### 3-2. 단위 테스트 — 20 case 신규 (전부 PASS)
- `:services:accounting-service:test` PASS — `SalesAggregateServiceTest 4` + `LedgerImageServiceTest 4` (PartnerLookup + ChatRoom + Journal 3 way) + `StatementBatchServiceTest 3` + `HometaxExportServiceTest 5` (POI workbook 직렬화 + 100건 sheet 분할 회귀) + `DailyClosingDetailServiceTest 4`

### 3-3. Desktop typecheck
- `cd clients/desktop && npm run typecheck` → tsc PASS (0 error)

### 3-4. Korean path JDK 17 트랩 회피
Windows + 한글 경로 + JDK 17 환경에서 Testcontainers IT 자동 skip (memory `feedback_korean_path_jdk` / `feedback_testcontainers_windows_docker`). CI Linux runner 에서 실 IT 동작 검증.

## 4. 후속 (PR-F 이후)

- **PR-F** — GAS C/D 6건 진입 (사용자 분류 C/D 도구) 별도 슬라이스
- **인쇄 양식 iteration** — PartnerLedgerView + StatementBatchView 2~5차 (사용자 Edge 캡처 → CSS-only 미세 조정, `feedback_print_design_iteration`)
- **POI 의존성 운영 진입 시점 정합** — Hometax export 양식 (구분/공급자사업자번호/공급받는자사업자번호/공급가액/세액/품목/규격/수량/단가/합계 등) 홈택스 v2026 표준 회귀 테스트 1건 추가 권장
- **외부 client 3종 cache** — Ledger/StatementBatch 의 PartnerLookup/ChatRoom 호출은 거래처 수 × 기간 × N 회 호출 패턴 — 운영 부하 진입 시점 short-TTL Caffeine cache 도입 검토

## 5. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — 본 PR scope 의 신규 entity 0 (조회 only). JournalLineRepository / TaxInvoiceRepository 는 read-only Specification.
- **Soft Delete 일관** — 모든 조회 `is_deleted = FALSE` 가드.
- **한국어 Javadoc** — AccountingReportController + 5 Service + 4 DTO + 3 Client 전부 한국어 Javadoc.
- **ROLE 풀네임** — `ACCOUNTANT` / `MASTER` `@PreAuthorize` (memory `feedback_role_naming_full`).
- **IT 외부 client `@MockBean` 격리** — ProductClient / PartnerLookupClient / ChatRoomMappingClient 본 PR scope IT 미진입 (read-only endpoint 단위 테스트 우선). 후속 SpringBootTest 진입 시점 의무 (memory `feedback_it_mockbean_external_clients`).
- **UUID 비공개** — 응답 DTO (LedgerImageResponse / StatementBatchRow / SalesAggregateRow / DailyClosingDetailResponse) 전부 partnerCode + partnerName + slipNo / taxInvoiceNo / journalNo 만 노출, `*_id` UUID 미노출 (memory `feedback_uuid_no_user_visibility`).
- **한국 일반기업회계기준 코드 일관** — 401 (제품매출) / 110 (외상매출금) / 255 (부가세예수금) (memory `project_korean_accounting`).
- **partner_code snapshot 의무** — Ledger/StatementBatch 응답은 거래 시점 snapshot (TaxInvoice/JournalLine 의 `partner_code` 컬럼 직접 read).

## 6. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR 은 5+1 = 6 commits 단일 통합 PR (Phase A 2 + Phase B 4 = 6 commits 실제, FE-10 git reset 으로 destroy 된 FE-8/FE-9 작업 복구 1 commit 흡수). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 7. multi-agent collision 안내 (working tree 복구)

FE-10 (`MonthEndClosingPage` 일별 detail 보강) 진행 시점, FE agent 의 `git reset --soft` 호출이 직전 FE-8 (`StatementBatchPage`) commit `eb473b4` + FE-9 (`HometaxExportPage`) commits `6cf9646` / `8f62b57` 를 destroy 하면서 작업물이 working tree 에 unstaged 로 남았다. TM 시점 working tree clean 검증 시 발견하여 단일 복구 commit `55ebad5` 으로 일괄 stage + commit (메시지 본문에 destroy 된 SHA 3건 명시).

- **rebase 정정 회피** — 기존 commit SHA 보존, dev-report § 2-2 + 본 절에 정합 안내
- **multi-agent race 추세** — PR-E1 시점 d163caa (FE-1+2+6 통합) 와 동일 패턴. 후속 PR-F 진입 시점 sequential commit 강제 또는 task 별 worktree 분리 검토

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 (개발책임자) 본인 머지
6. 머지 후 연관 Issue 즉시 close (memory `feedback_issue_close_after_pr`)
